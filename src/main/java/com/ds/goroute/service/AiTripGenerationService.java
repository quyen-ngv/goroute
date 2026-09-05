package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.entity.*;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.AiTripGenerationMapper;
import com.ds.goroute.repository.*;
import com.ds.goroute.type.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ds.goroute.repository.AppConfigRepository;
import com.ds.goroute.thirdparty.goong.GoongClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.util.LinkedMultiValueMap;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTripGenerationService {
    private final AiTripGenerationMapper mapper;
    private final AiTripQuotaService quotaService;
    private final PlaceRepository placeRepository;
    private final TripService tripService;
    private final ActivityRepository activityRepository;
    private final ObjectMapper objectMapper;
    private final AiTripSseService sseService;
    private final AppConfigRepository appConfigRepository;
    private final NotificationService notificationService;
    private final GoongClient goongClient;
    /** Local cache only: it reduces repeated resolver calls; it is never a source of truth. */
    private final Cache<String, Map<String, Object>> candidateResolutionCache = Caffeine.newBuilder()
            .maximumSize(10_000).expireAfterWrite(6, TimeUnit.HOURS).build();

    /**
     * Creates the durable AI job in its own transaction. Social-video completion first stores
     * the extracted spots and then calls this method; an exhausted AI quota must not roll back
     * those already-useful saved spots in the surrounding social-job transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiTripGenerationJob create(AiTripGenerateRequest request, UUID userId, String idempotencyKey, String locale) {
        validateRequest(request);
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? UUID.randomUUID().toString() : idempotencyKey.trim();
        String payload = json(request);
        String hash = sha256(payload);
        log.info("AI trip create: user={} city={} destinations={} pace={} locale={}", userId, request.getCityName(),
                request.getDestinations() == null ? 0 : request.getDestinations().size(), request.getPace(), locale);
        AiTripGenerationJob existing = mapper.findByUserAndKey(userId, key);
        if (existing != null) {
            log.info("AI trip create: idempotent hit, returning existing job {} (status={})", existing.getId(), existing.getStatus());
            if (!existing.getRequestHash().equals(hash)) throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Idempotency key was already used for another request");
            return existing;
        }
        quotaService.reserve(userId);
        AiTripGenerationJob job = new AiTripGenerationJob();
        job.setId(UUID.randomUUID()); job.setUserId(userId); job.setIdempotencyKey(key); job.setRequestHash(hash);
        job.setRequestPayload(payload); job.setLocale(normalizeLocale(locale)); job.setAttemptId(UUID.randomUUID().toString());
        job.setStatus("QUEUED"); job.setStage("QUEUED"); job.setProgress(0); job.setQuotaStatus("RESERVED");
        mapper.insertJob(job);
        AiTripGenerationEvent event = event(job, "QUEUED", "QUEUED", 0, "ai_trip.queued", Map.of());
        mapper.insertEvent(event);
        log.info("AI trip create: job {} QUEUED for user {} (attempt {})", job.getId(), userId, job.getAttemptId());
        return mapper.findById(job.getId());
    }

    public AiTripJobResponse get(UUID jobId, UUID userId) { return AiTripJobResponse.from(owned(jobId, userId)); }
    public AiTripJobResponse active(UUID userId) {
        AiTripGenerationJob job = mapper.findActiveByUser(userId);
        return job == null ? null : AiTripJobResponse.from(job);
    }
    public SseEmitter subscribe(UUID jobId, UUID userId, long afterId) { owned(jobId, userId); return sseService.subscribe(jobId, afterId); }

    @Transactional
    public void acceptEvent(UUID jobId, AiTripJobEventRequest request) {
        AiTripGenerationJob job = mapper.findByIdForUpdate(jobId);
        if (job == null || !job.getAttemptId().equals(request.getAttemptId()) || terminal(job.getStatus())) {
            log.info("AI trip event ignored for job {}: stage={} status={} (job={}, staleAttempt={})", jobId,
                    request.getStage(), request.getStatus(), job == null ? "missing" : job.getStatus(),
                    job != null && !job.getAttemptId().equals(request.getAttemptId()));
            return;
        }
        log.info("AI trip event: job {} stage={} status={} progress={}{}", jobId, request.getStage(), request.getStatus(),
                request.getProgress(), request.getErrorMessage() == null ? "" : " error=" + request.getErrorMessage());
        if ("FAILED".equals(request.getStatus()) || "CANCELLED".equals(request.getStatus())) {
            terminal(job, request.getStatus(), request.getErrorMessage());
            return;
        }
        int progress = Math.max(job.getProgress(), request.getProgress());
        mapper.updateProgress(jobId, job.getAttemptId(), "RUNNING", request.getStage(), progress, null);
        AiTripGenerationEvent event = event(job, request.getStage(), "RUNNING", progress,
                request.getMessageKey(), request.getParams() == null ? Map.of() : request.getParams());
        mapper.insertEvent(event); sseService.publish(event);
    }

    /** Editable prompt rules for the AI worker: every active row under config label AI_TRIP. */
    public Map<String,String> promptConfig(UUID jobId, String attemptId) {
        verifyAttempt(mapper.findById(jobId), attemptId);
        Map<String,String> out = new LinkedHashMap<>();
        for (var c : appConfigRepository.findAdmin(null, "AI_TRIP", true, 200, 0)) out.put(c.getKey(), c.getValue());
        return out;
    }

    public List<Map<String,Object>> candidates(UUID jobId, String attemptId, AiTripCandidateQueryRequest request) {
        AiTripGenerationJob job = mapper.findById(jobId);
        verifyAttempt(job, attemptId);
        List<Place> places = placeRepository.findActiveForAiWithinRadius(request.getLatitude(), request.getLongitude(),
                BigDecimal.valueOf(50), request.getPlaceGroups(), request.getLimit());
        List<Map<String,Object>> result = new ArrayList<>();
        for (Place p : places) {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id", p.getId()); row.put("googlePlaceId", p.getPlaceId()); row.put("title", p.getTitle()); row.put("address", p.getAddress());
            row.put("openHours", tree(p.getOpenHours()));
            row.put("latitude", p.getLatitude()); row.put("longitude", p.getLongitude()); row.put("placeGroup", p.getPlaceGroup());
            row.put("category", p.getCategory()); row.put("reviewCount", p.getReviewCount()); row.put("reviewRating", p.getReviewRating());
            row.put("score", p.getPlaceOverallScore()); row.put("distanceKm", p.getDistance()); row.put("visitDurationMinutes", p.getVisitDurationMinutes());
            row.put("description", localizedDescription(p, job.getLocale()));
            row.put("attributes", valuedAttributes(p.getAttributes()));
            row.put("menuHighlights", highlightTitles(p.getMenu()));
            result.add(row);
        }
        log.info("AI trip candidates: job {} groups={} -> {} places", jobId, request.getPlaceGroups(), result.size());
        return result;
    }

    /**
     * Resolve names discovered by cited Web research. Catalogue matches must be a unique exact
     * normalized title; otherwise Goong is queried server-side and is accepted only when its
     * returned canonical name is also an exact normalized match near the destination.  An
     * unresolved row deliberately has no address or coordinates.
     */
    public List<Map<String, Object>> resolveCandidates(UUID jobId, String attemptId, AiTripCandidateResolutionRequest request) {
        AiTripGenerationJob job = mapper.findById(jobId);
        verifyAttempt(job, attemptId);
        if (request.getCandidates() == null || request.getCandidates().isEmpty()) return List.of();
        List<String> groups = request.getCandidates().stream().map(AiTripCandidateResolutionRequest.Candidate::getPlaceGroup)
                .filter(Objects::nonNull).distinct().toList();
        List<Place> catalogue = placeRepository.findActiveForAiWithinRadius(request.getLatitude(), request.getLongitude(),
                BigDecimal.valueOf(50), groups, 500);
        List<Map<String, Object>> resolved = new ArrayList<>();
        for (AiTripCandidateResolutionRequest.Candidate candidate : request.getCandidates()) {
            String key = resolutionKey(request, candidate);
            Map<String, Object> value = candidateResolutionCache.get(key,
                    ignored -> resolveCandidate(candidate, request.getLatitude(), request.getLongitude(), catalogue, job.getLocale()));
            Map<String, Object> row = new LinkedHashMap<>(value);
            row.put("candidateId", candidate.getCandidateId());
            resolved.add(row);
        }
        log.info("AI trip Web candidate resolution: job {} candidates={} resolved={}", jobId, resolved.size(),
                resolved.stream().filter(row -> Boolean.TRUE.equals(row.get("externalResolutionVerified")) || row.get("id") != null).count());
        return resolved;
    }

    private Map<String, Object> resolveCandidate(AiTripCandidateResolutionRequest.Candidate candidate, BigDecimal latitude,
                                                   BigDecimal longitude, List<Place> catalogue, String locale) {
        List<Place> exact = catalogue.stream()
                .filter(place -> Objects.equals(place.getPlaceGroup(), candidate.getPlaceGroup()))
                .filter(place -> normalizedName(place.getTitle()).equals(normalizedName(candidate.getTitle())))
                .toList();
        if (exact.size() == 1) {
            Place place = exact.get(0);
            Map<String, Object> row = catalogueRow(place, locale);
            row.put("resolvedBy", "CATALOGUE");
            row.put("resolutionStatus", "RESOLVED");
            row.put("externalResolutionVerified", false);
            return row;
        }
        Map<String, Object> goong = resolveWithGoong(candidate, latitude, longitude);
        if (goong != null) return goong;
        return unresolvedRow(candidate);
    }

    private Map<String, Object> catalogueRow(Place p, String locale) {
        Map<String,Object> row = new LinkedHashMap<>();
        row.put("id", p.getId()); row.put("googlePlaceId", p.getPlaceId()); row.put("title", p.getTitle()); row.put("address", p.getAddress());
        row.put("openHours", tree(p.getOpenHours())); row.put("latitude", p.getLatitude()); row.put("longitude", p.getLongitude());
        row.put("placeGroup", p.getPlaceGroup()); row.put("category", p.getCategory()); row.put("reviewCount", p.getReviewCount());
        row.put("reviewRating", p.getReviewRating()); row.put("score", p.getPlaceOverallScore()); row.put("distanceKm", p.getDistance());
        row.put("visitDurationMinutes", p.getVisitDurationMinutes()); row.put("description", localizedDescription(p, locale));
        row.put("attributes", valuedAttributes(p.getAttributes())); row.put("menuHighlights", highlightTitles(p.getMenu()));
        return row;
    }

    private Map<String, Object> resolveWithGoong(AiTripCandidateResolutionRequest.Candidate candidate,
                                                  BigDecimal latitude, BigDecimal longitude) {
        try {
            LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("input", candidate.getTitle());
            params.add("location", latitude + "," + longitude);
            params.add("radius", "50000");
            params.add("limit", "5");
            var autocomplete = goongClient.forward("/Place/AutoComplete", params);
            if (!autocomplete.getStatusCode().is2xxSuccessful() || autocomplete.getBody() == null) return null;
            JsonNode predictions = objectMapper.readTree(autocomplete.getBody()).path("predictions");
            if (!predictions.isArray()) return null;
            for (JsonNode prediction : predictions) {
                String predictionName = text(prediction.path("structured_formatting").path("main_text"));
                if (predictionName == null) predictionName = text(prediction.path("name"));
                String placeId = text(prediction.path("place_id"));
                if (placeId == null || !normalizedName(candidate.getTitle()).equals(normalizedName(predictionName))) continue;
                LinkedMultiValueMap<String, String> detailParams = new LinkedMultiValueMap<>();
                detailParams.add("place_id", placeId);
                var detail = goongClient.forward("/Place/Detail", detailParams);
                if (!detail.getStatusCode().is2xxSuccessful() || detail.getBody() == null) continue;
                JsonNode result = objectMapper.readTree(detail.getBody()).path("result");
                String canonicalName = text(result.path("name"));
                BigDecimal lat = decimal(result.path("geometry").path("location").path("lat"));
                BigDecimal lng = decimal(result.path("geometry").path("location").path("lng"));
                if (!normalizedName(candidate.getTitle()).equals(normalizedName(canonicalName)) || lat == null || lng == null
                        || haversineKm(latitude, longitude, lat, lng) > 50) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("googlePlaceId", placeId); row.put("title", canonicalName); row.put("address", text(result.path("formatted_address")));
                row.put("latitude", lat); row.put("longitude", lng); row.put("placeGroup", candidate.getPlaceGroup());
                row.put("category", text(result.path("types").isArray() && result.path("types").size() > 0 ? result.path("types").get(0) : null));
                row.put("resolvedBy", "GOONG"); row.put("resolutionStatus", "RESOLVED"); row.put("externalResolutionVerified", true);
                return row;
            }
        } catch (Exception e) {
            log.warn("AI trip Goong resolver failed for '{}': {}", candidate.getTitle(), e.getMessage());
        }
        return null;
    }

    private Map<String, Object> unresolvedRow(AiTripCandidateResolutionRequest.Candidate candidate) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", candidate.getTitle()); row.put("placeGroup", candidate.getPlaceGroup());
        row.put("resolutionStatus", "UNRESOLVED"); row.put("externalResolutionVerified", false);
        return row;
    }

    private String resolutionKey(AiTripCandidateResolutionRequest request, AiTripCandidateResolutionRequest.Candidate candidate) {
        return normalizedName(candidate.getTitle()) + "|" + candidate.getPlaceGroup() + "|"
                + request.getLatitude().setScale(3, java.math.RoundingMode.HALF_UP) + "|" + request.getLongitude().setScale(3, java.math.RoundingMode.HALF_UP);
    }

    private String normalizedName(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{Alnum}]+", " ").trim().replaceAll("\\s+", " ");
    }

    private String text(JsonNode node) {
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText().trim() : null;
    }

    private BigDecimal decimal(JsonNode node) {
        try { return node != null && node.isNumber() ? node.decimalValue() : null; } catch (Exception ignored) { return null; }
    }

    private double haversineKm(BigDecimal aLat, BigDecimal aLng, BigDecimal bLat, BigDecimal bLng) {
        double dLat = Math.toRadians(bLat.doubleValue() - aLat.doubleValue());
        double dLng = Math.toRadians(bLng.doubleValue() - aLng.doubleValue());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(aLat.doubleValue()))
                * Math.cos(Math.toRadians(bLat.doubleValue())) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    @Transactional
    public UUID commit(UUID jobId, AiTripCommitRequest request) {
        AiTripGenerationJob job = mapper.findByIdForUpdate(jobId);
        verifyAttempt(job, request.getAttemptId());
        log.info("AI trip commit: job {} with {} items", jobId, request.getItems().size());
        if ("COMPLETED".equals(job.getStatus())) return job.getCreatedTripId();
        if (terminal(job.getStatus())) throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "AI job is already terminal");
        AiTripGenerateRequest original = read(job.getRequestPayload(), AiTripGenerateRequest.class);
        validateItems(original, request.getItems());
        List<TripDestinationRequest> destinations = new ArrayList<>();
        for (int i=0; i<original.getDestinations().size(); i++) {
            AiTripDestinationRequest d = original.getDestinations().get(i);
            String name=d.getName()==null||d.getName().isBlank()?original.getCityName():d.getName();
            destinations.add(TripDestinationRequest.builder().name(name).address(name)
                    .lat(d.getLatitude()).lng(d.getLongitude()).orderIndex(i).startDate(d.getStartDate()).endDate(d.getEndDate()).isPrimary(i==0).build());
        }
        AiTripDestinationRequest first = original.getDestinations().get(0);
        AiTripDestinationRequest last = original.getDestinations().get(original.getDestinations().size()-1);
        TripResponse trip = tripService.createTrip(CreateTripRequest.builder().name(original.getTripName() == null || original.getTripName().isBlank() ? original.getCityName() : original.getTripName())
                .startDate(first.getStartDate()).endDate(last.getEndDate()).destination(original.getCityName())
                .destinationLat(first.getLatitude()).destinationLng(first.getLongitude()).destinations(destinations)
                .budget(original.getBudgetMax()).currency(original.getBudgetCurrency()).build(), job.getUserId());
        if (request.getTripDescription() != null) tripService.updateTrip(trip.getId(), UpdateTripRequest.builder().description(request.getTripDescription()).build(), job.getUserId());
        for (AiTripCommitRequest.Item item : request.getItems()) activityRepository.insert(toActivity(item, trip.getId(), job.getUserId()));
        int completed = mapper.markCompleted(jobId, job.getAttemptId(), trip.getId());
        log.info("AI trip commit: job {} COMPLETED -> trip {}", jobId, trip.getId());
        AiTripGenerationEvent event = event(job, "COMPLETED", "COMPLETED", 100, "ai_trip.completed", Map.of("tripId", trip.getId()));
        mapper.insertEvent(event); sseService.publish(event);
        if (completed == 1) {
            notificationService.createNotification(
                    job.getUserId(), trip.getId(), NotificationType.AI_TRIP_CREATED,
                    null, null,
                    Map.of("tripId", trip.getId().toString(), "deepLink", "/trip/" + trip.getId()),
                    null);
        }
        return trip.getId();
    }

    @Transactional public void cancel(UUID jobId, UUID userId) { AiTripGenerationJob job=owned(jobId,userId); terminal(job,"CANCELLED",null); }
    @Transactional public void fail(UUID jobId, String error) { AiTripGenerationJob job=mapper.findByIdForUpdate(jobId); if(job!=null&&!terminal(job.getStatus())) terminal(job,"FAILED",error); }

    private void terminal(AiTripGenerationJob job, String status, String error) {
        log.info("AI trip terminal: job {} -> {}{}", job.getId(), status, error == null ? "" : " (" + error + ")");
        if (mapper.markTerminalAndRelease(job.getId(), status, error) == 1) quotaService.release(job.getUserId());
        AiTripGenerationEvent event=event(job,status,status,job.getProgress(),"ai_trip."+status.toLowerCase(Locale.ROOT),Map.of());
        mapper.insertEvent(event); sseService.publish(event);
    }
    private Activity toActivity(AiTripCommitRequest.Item i, UUID tripId, UUID userId) {
        Place p = i.getPlaceId()==null ? null : placeRepository.findById(i.getPlaceId()).filter(x -> x.getVisibilityStatus()==PlaceVisibilityStatus.ACTIVE).orElseThrow(() -> new BusinessException(ErrorConstant.INVALID_PARAMETERS,"Selected place is unavailable"));
        boolean transport="TRANSPORT".equals(i.getType());
        TransportMode mode=null; if (i.getTransportMode()!=null) try { mode=TransportMode.valueOf(i.getTransportMode()); } catch(Exception e){ throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,"Invalid transport mode"); }
        // places.title is VARCHAR(500) but activities.name is VARCHAR(255): a long Google Maps
        // title (or a transport label built from it) must be truncated, not fail the whole commit.
        boolean unresolvedWebCandidate = i.getCandidateId() != null && i.getCandidateId().startsWith("web:")
                && !"GOONG".equals(i.getExternalPlaceResolution());
        String address = p != null && p.getAddress() != null && !p.getAddress().isBlank()
                ? p.getAddress() : unresolvedWebCandidate ? null : i.getAddress();
        BigDecimal latitude = p != null && p.getLatitude() != null ? p.getLatitude() : unresolvedWebCandidate ? null : i.getLatitude();
        BigDecimal longitude = p != null && p.getLongitude() != null ? p.getLongitude() : unresolvedWebCandidate ? null : i.getLongitude();
        return Activity.builder().id(UUID.randomUUID()).tripId(tripId).dayNumber(i.getDayNumber()).sortOrder(i.getSortOrder())
                .placeRefId(p==null?null:p.getId()).placeId(p==null?null:p.getPlaceId()).name(trunc(i.getName(),255))
                .address(trunc(address,500)).lat(latitude).lng(longitude)
                .endAddress(transport?trunc(i.getEndAddress(),500):null).endLat(transport?i.getEndLatitude():null).endLng(transport?i.getEndLongitude():null)
                .startTime(i.getStartTime()).endTime(i.getEndTime()).endDayNumber(i.getEndDayNumber()).category(transport?"transport":trunc(i.getCategory(),50))
                .transportMode(mode).durationToNext(trunc(i.getDurationToNext(),64)).durationValueToNext(i.getDurationValueToNext())
                .distanceToNext(trunc(i.getDistanceToNext(),64)).distanceValueToNext(i.getDistanceValueToNext())
                .description(i.getDescription()).notes(i.getNotes()).status(ActivityStatus.CONFIRMED).addedBy(userId)
                .optionGroupId(trunc(i.getOptionGroupId(), 128)).optionIndex(i.getOptionIndex()).relation(trunc(i.getRelation(), 32))
                .sourceSocialJobId(uuid(i.getSourceSocialJobId())).sourceSocialCandidateRef(trunc(i.getSourceSocialCandidateRef(), 128))
                .build();
    }
    private String trunc(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }
    private void validateItems(AiTripGenerateRequest r, List<AiTripCommitRequest.Item> items) {
        int days = (int) ChronoUnit.DAYS.between(
                r.getDestinations().get(0).getStartDate(),
                r.getDestinations().get(r.getDestinations().size() - 1).getEndDate()) + 1;
        Map<Integer, LocalTime> ends = new HashMap<>();
        Map<Integer, Map<String, LocalTime>> optionEnds = new HashMap<>();
        for (var item : items) {
            if (item.getDayNumber() < 1 || item.getDayNumber() > days) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Activity day is outside trip");
            }
            if (item.getStartTime() != null && item.getEndTime() != null
                    && item.getEndDayNumber() == null && !item.getEndTime().isAfter(item.getStartTime())) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Invalid activity time");
            }
            String optionGroup = item.getOptionGroupId();
            if (optionGroup != null) optionGroup = optionGroup.trim();
            if (optionGroup != null && optionGroup.isBlank()) optionGroup = null;
            LocalTime optionEnd = optionGroup == null ? null
                    : optionEnds.computeIfAbsent(item.getDayNumber(), ignored -> new HashMap<>()).get(optionGroup);
            LocalTime previousEnd = ends.get(item.getDayNumber());
            if (previousEnd != null && item.getStartTime() != null
                    && item.getStartTime().isBefore(previousEnd)
                    && optionEnd == null) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Activities overlap");
            }
            if (item.getEndTime() != null) {
                LocalTime end = item.getEndTime();
                if (optionGroup != null) {
                    optionEnds.get(item.getDayNumber()).merge(optionGroup, end,
                            (left, right) -> left.isAfter(right) ? left : right);
                }
                LocalTime currentEnd = ends.get(item.getDayNumber());
                if (currentEnd == null || end.isAfter(currentEnd)) ends.put(item.getDayNumber(), end);
            }
            if ("PLACE_VISIT".equals(item.getType()) && item.getPlaceId() == null) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Place visit needs placeId");
            }
        }
    }
    private UUID uuid(String value) {
        if (value == null || value.isBlank()) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    private void validateRequest(AiTripGenerateRequest r) {
        if (r.getDestinations() == null || r.getDestinations().isEmpty()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "At least one destination is required");
        }
        boolean social = r.getSocialContext() != null;
        for (var destination : r.getDestinations()) {
            if ((!social && (destination.getLatitude() == null || destination.getLongitude() == null))
                    || destination.getStartDate() == null || destination.getEndDate() == null) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Destination coordinates and dates are required");
            }
        }
    }
    private AiTripGenerationJob owned(UUID id,UUID user){ AiTripGenerationJob j=mapper.findById(id); if(j==null||!j.getUserId().equals(user)) throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,"AI job not found"); return j; }
    private void verifyAttempt(AiTripGenerationJob j,String a){ if(j==null||!Objects.equals(j.getAttemptId(),a)) throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,"Stale AI job attempt"); }
    private boolean terminal(String s){return Set.of("COMPLETED","FAILED","CANCELLED").contains(s);}
    private AiTripGenerationEvent event(AiTripGenerationJob j,String stage,String status,int progress,String key,Map<String,Object> params){AiTripGenerationEvent e=new AiTripGenerationEvent();e.setJobId(j.getId());e.setAttemptId(j.getAttemptId());e.setStage(stage);e.setStatus(status);e.setProgress(progress);e.setMessageKey(key);e.setParams(json(params));return e;}
    private String normalizeLocale(String s){return s!=null&&s.toLowerCase().startsWith("vi")?"vi":"en";}
    private String localizedDescription(Place p,String locale){if(p.getAiDescription()!=null&&!p.getAiDescription().isBlank())return p.getAiDescription(); Object x=treeValue(p.getDescriptions(),locale);return x==null?null:String.valueOf(x);}
    private Object valuedAttributes(String raw){Object x=prune(tree(raw));return x==null?Map.of():x;}
    private Object prune(Object x){if(x==null)return null;if(x instanceof String s)return s.isBlank()?null:s;if(x instanceof Map<?,?> m){Map<String,Object> out=new LinkedHashMap<>();for(var e:m.entrySet()){Object v=prune(e.getValue());if(v!=null)out.put(String.valueOf(e.getKey()),v);}return out.isEmpty()?null:out;}if(x instanceof List<?> l){List<Object> out=l.stream().map(this::prune).filter(Objects::nonNull).toList();return out.isEmpty()?null:out;}return x;}
    private List<String> highlightTitles(String raw){Object x=tree(raw);List<String> out=new ArrayList<>();collectTitles(x,out);return out.stream().filter(t->!t.matches("(?i)photo \\d+ of \\d+")).distinct().limit(30).toList();}
    @SuppressWarnings("unchecked") private void collectTitles(Object x,List<String> out){if(x instanceof Map<?,?> m){Object h=m.get("highlightTitle");Object t=m.get("title");if(h!=null)out.add(String.valueOf(h));else if(t!=null)out.add(String.valueOf(t));for(Object v:m.values())collectTitles(v,out);}else if(x instanceof List<?> l)for(Object v:l)collectTitles(v,out);}
    private Object treeValue(String raw,String key){Object x=tree(raw);return x instanceof Map<?,?>m?m.get(key):x;}
    private Object tree(String raw){try{return raw==null?null:objectMapper.readValue(raw,new TypeReference<Object>(){});}catch(Exception e){return null;}}
    private <T>T read(String raw,Class<T> type){try{return objectMapper.readValue(raw,type);}catch(Exception e){throw new IllegalStateException(e);}}
    private String json(Object x){try{return objectMapper.writeValueAsString(x);}catch(Exception e){throw new IllegalStateException(e);}}
    private String sha256(String x){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(x.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
