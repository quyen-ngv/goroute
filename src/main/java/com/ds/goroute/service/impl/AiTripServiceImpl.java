package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.AiTripConfirmRequest;
import com.ds.goroute.dto.request.AiTripGenerateRequest;
import com.ds.goroute.dto.request.CreateTripRequest;
import com.ds.goroute.dto.request.UpdateTripRequest;
import com.ds.goroute.dto.response.AiTripCandidateResponse;
import com.ds.goroute.dto.response.AiTripConfirmResponse;
import com.ds.goroute.dto.response.AiTripGenerateResponse;
import com.ds.goroute.dto.response.TripResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.ActivityBooking;
import com.ds.goroute.entity.AiTripDraft;
import com.ds.goroute.entity.Place;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.ActivityBookingGeoSearchParams;
import com.ds.goroute.repository.ActivityBookingRepository;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.AiTripService;
import com.ds.goroute.service.TripService;
import com.ds.goroute.thirdparty.claude.ClaudeClient;
import com.ds.goroute.type.ActivityStatus;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.TransportMode;
import com.ds.goroute.utils.AiTripGenerationSummary;
import com.ds.goroute.utils.AiTripLanguageSupport;
import com.ds.goroute.utils.DestinationMatchUtils;
import com.ds.goroute.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTripServiceImpl implements AiTripService {

    private static final int FREE_LIMIT = 3;
    private static final int PRO_LIMIT = 10;
    private static final double SEARCH_RADIUS_KM = 80.0;
    private static final String AI_TRIP_SYSTEM_CONTEXT = """
            This is a travel planning app for foreign tourists visiting Vietnam.
            Place names, addresses, and descriptions may be in Vietnamese - treat them as ground truth.
            """;

    private final AiTripRepository aiTripRepository;
    private final PlaceRepository placeRepository;
    private final ActivityBookingRepository activityBookingRepository;
    private final ActivityRepository activityRepository;
    private final TripService tripService;
    private final ClaudeClient claudeClient;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AiTripGenerateResponse generateCandidates(AiTripGenerateRequest request, UUID userId) {
        validateGenerateRequest(request);

        aiTripRepository.ensureSubscription(userId);
        if (aiTripRepository.consumeAiTripQuota(userId) == 0) {
            throw new BusinessException(ErrorConstant.AI_TRIP_QUOTA_EXHAUSTED);
        }

        String tier = aiTripRepository.getSubscriptionTier(userId);
        int used = aiTripRepository.getAiTripsUsed(userId);
        int limit = limitForTier(tier);

        List<PlaceGroup> groups = normalizeGroups(request.getPlaceGroups());
        List<AiTripCandidateResponse> candidates = collectCandidates(request, groups);
        candidates = rankCandidatesWithClaude(request, candidates);

        UUID draftId = UUID.randomUUID();
        AiTripDraft draft = AiTripDraft.builder()
                .id(draftId)
                .userId(userId)
                .tripName(defaultTripName(request))
                .cityId(request.getCityId())
                .cityName(request.getCityName())
                .cityLat(request.getCityLat())
                .cityLng(request.getCityLng())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .dayCount(resolveDayCount(request))
                .placeGroups(JsonUtils.toJson(groups.stream().map(Enum::name).toList()))
                .pace(normalizePace(request.getPace()))
                .preferenceText(request.getPreferenceText())
                .candidates(JsonUtils.toJson(candidates))
                .status("PENDING")
                .build();
        aiTripRepository.insertDraft(draft);

        return AiTripGenerateResponse.builder()
                .draftId(draftId)
                .tier(tier)
                .aiTripsUsed(used)
                .aiTripLimit(limit)
                .candidates(candidates)
                .build();
    }

    @Override
    @Transactional
    public AiTripConfirmResponse confirmTrip(UUID draftId, AiTripConfirmRequest request, UUID userId) {
        AiTripDraft draft = aiTripRepository.findDraftForUpdate(draftId, userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "AI trip draft not found"));

        if ("COMPLETED".equals(draft.getStatus())) {
            return completedResponse(draft);
        }
        if (!"PENDING".equals(draft.getStatus())) {
            throw new BusinessException(ErrorConstant.AI_TRIP_DRAFT_INACTIVE);
        }

        List<AiTripCandidateResponse> candidates = parseCandidates(draft.getCandidates());
        Map<String, AiTripCandidateResponse> candidatesById = candidates.stream()
                .collect(Collectors.toMap(AiTripCandidateResponse::getId, Function.identity(), (a, b) -> a));

        List<String> selectedIds = request.getSelectedCandidateIds() == null
                ? List.of()
                : request.getSelectedCandidateIds().stream().distinct().toList();
        List<AiTripCandidateResponse> selected = selectedIds.stream()
                .map(candidatesById::get)
                .filter(Objects::nonNull)
                .toList();

        List<ScheduledCandidate> schedule = scheduleCandidatesWithClaude(draft, selected);
        Set<String> scheduledIds = schedule.stream()
                .map(item -> item.candidate().getId())
                .collect(Collectors.toSet());
        List<AiTripCandidateResponse> skipped = selected.stream()
                .filter(candidate -> !scheduledIds.contains(candidate.getId()))
                .toList();

        Map<String, String> visitTips = generateVisitTips(draft, schedule);
        int filledDays = schedule.stream().map(ScheduledCandidate::dayNumber).max(Integer::compareTo).orElse(0);
        String generationSummary = buildGenerationSummary(draft, selected.size(), schedule, skipped, filledDays);
        String coverageMessage = buildCoverageMessage(selected.size(), schedule.size(), filledDays, draft.getDayCount());

        TripResponse trip = tripService.createTrip(CreateTripRequest.builder()
                .name(draft.getTripName() != null && !draft.getTripName().isBlank() ? draft.getTripName() : draft.getCityName())
                .destination(draft.getCityName())
                .destinationPlaceId(draft.getCityId())
                .destinationLat(draft.getCityLat())
                .destinationLng(draft.getCityLng())
                .startDate(draft.getStartDate())
                .endDate(draft.getEndDate())
                .currency("VND")
                .build(), userId);

        if (generationSummary != null && !generationSummary.isBlank()) {
            trip = tripService.updateTrip(trip.getId(),
                    UpdateTripRequest.builder().description(generationSummary).build(),
                    userId);
        }

        for (ScheduledCandidate item : schedule) {
            activityRepository.insert(toActivity(trip.getId(), item, userId, visitTips));
        }

        aiTripRepository.completeDraft(draftId, userId, request.getIdempotencyKey(), trip.getId());

        List<String> skippedNames = skipped.stream()
                .map(AiTripCandidateResponse::getName)
                .filter(name -> name != null && !name.isBlank())
                .toList();

        return AiTripConfirmResponse.builder()
                .trip(trip)
                .selectedCount(selected.size())
                .scheduledCount(schedule.size())
                .filledDays(filledDays)
                .totalDays(draft.getDayCount())
                .coverageMessage(coverageMessage)
                .generationSummary(generationSummary)
                .skippedPlaceNames(skippedNames)
                .build();
    }

    private void validateGenerateRequest(AiTripGenerateRequest request) {
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Start date must be before end date");
        }
        int dayCount = resolveDayCount(request);
        if (dayCount <= 0 || dayCount > 30) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Day count must be between 1 and 30");
        }
    }

    private int resolveDayCount(AiTripGenerateRequest request) {
        if (request.getDayCount() != null && request.getDayCount() > 0) {
            return request.getDayCount();
        }
        return (int) (request.getEndDate().toEpochDay() - request.getStartDate().toEpochDay()) + 1;
    }

    private String defaultTripName(AiTripGenerateRequest request) {
        return request.getTripName() != null && !request.getTripName().isBlank()
                ? request.getTripName().trim()
                : request.getCityName();
    }

    private List<PlaceGroup> normalizeGroups(List<PlaceGroup> groups) {
        EnumSet<PlaceGroup> normalized = EnumSet.noneOf(PlaceGroup.class);
        if (groups == null || groups.isEmpty()) {
            normalized.addAll(Arrays.stream(PlaceGroup.values())
                    .filter(group -> group != PlaceGroup.OTHER)
                    .toList());
        } else {
            groups.stream()
                    .filter(group -> group != null && group != PlaceGroup.OTHER)
                    .forEach(normalized::add);
        }
        normalized.add(PlaceGroup.ACCOMMODATION);
        return normalized.stream().toList();
    }

    private String normalizePace(String pace) {
        if (pace == null || pace.isBlank()) {
            return "BALANCED";
        }
        String value = pace.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "RELAXED", "EAGER" -> value;
            default -> "BALANCED";
        };
    }

    private int limitForTier(String tier) {
        return "PRO".equalsIgnoreCase(tier) ? PRO_LIMIT : FREE_LIMIT;
    }

    private List<AiTripCandidateResponse> collectCandidates(AiTripGenerateRequest request, List<PlaceGroup> groups) {
        int dayCount = resolveDayCount(request);
        int target = Math.min(30, Math.max(12, dayCount * 6));
        List<AiTripCandidateResponse> candidates = new ArrayList<>();

        for (PlaceGroup group : groups) {
            int groupLimit = group == PlaceGroup.ACCOMMODATION ? 4 : Math.max(4, dayCount * 2);
            List<Place> places = findPlaces(request, group, groupLimit);
            for (Place place : places) {
                candidates.add(fromPlace(place));
            }
        }

        candidates.addAll(findBookings(request, Math.max(6, dayCount * 3)).stream()
                .map(this::fromBooking)
                .toList());

        Map<String, AiTripCandidateResponse> deduped = new LinkedHashMap<>();
        for (AiTripCandidateResponse candidate : candidates) {
            String key = normalizeKey(candidate.getName() + "|" + candidate.getAddress());
            deduped.putIfAbsent(key, candidate);
        }

        return deduped.values().stream()
                .sorted(Comparator.comparing(this::candidateScore).reversed())
                .limit(target)
                .toList();
    }

    private List<Place> findPlaces(AiTripGenerateRequest request, PlaceGroup group, int limit) {
        if (request.getCityLat() != null && request.getCityLng() != null) {
            return placeRepository.findNearby(null, request.getCityLat(), request.getCityLng(),
                    BigDecimal.valueOf(SEARCH_RADIUS_KM), null, List.of(group.name()), BigDecimal.valueOf(3.5), false, limit, 0);
        }
        String cityKey = DestinationMatchUtils.normalizeKey(request.getCityName());
        return placeRepository.findAll().stream()
                .filter(place -> place.getPlaceGroup() == group)
                .filter(place -> matchesCity(place.getDestinations(), place.getAddress(), request.getCityName(), cityKey))
                .limit(limit)
                .toList();
    }

    private List<ActivityBooking> findBookings(AiTripGenerateRequest request, int limit) {
        if (request.getCityLat() != null && request.getCityLng() != null) {
            double lat = request.getCityLat().doubleValue();
            double lng = request.getCityLng().doubleValue();
            double latDelta = SEARCH_RADIUS_KM / 111.0;
            double lngDelta = SEARCH_RADIUS_KM / (111.0 * Math.max(0.2, Math.cos(Math.toRadians(lat))));
            return activityBookingRepository.findWithinRadius(ActivityBookingGeoSearchParams.builder()
                    .latitude(request.getCityLat())
                    .longitude(request.getCityLng())
                    .radiusKm(SEARCH_RADIUS_KM)
                    .minLat(lat - latDelta)
                    .maxLat(lat + latDelta)
                    .minLng(lng - lngDelta)
                    .maxLng(lng + lngDelta)
                    .minRating(BigDecimal.valueOf(3.5))
                    .limit(limit)
                    .offset(0)
                    .build());
        }
        List<String> keys = DestinationMatchUtils.parseFilterValues(List.of(request.getCityName())).stream()
                .map(DestinationMatchUtils::normalizeKey)
                .filter(key -> !key.isBlank())
                .toList();
        return keys.isEmpty() ? List.of() : activityBookingRepository.findByDestinations(keys, limit, 0);
    }

    private boolean matchesCity(String destinationsJson, String address, String cityName, String cityKey) {
        List<String> destinations = JsonUtils.fromJson(destinationsJson, new TypeReference<List<String>>() {
        });
        if (DestinationMatchUtils.matches(destinations, List.of(cityName))) {
            return true;
        }
        return DestinationMatchUtils.normalizeKey(address).contains(cityKey);
    }

    private AiTripCandidateResponse fromPlace(Place place) {
        Integer duration = place.getVisitDurationMinutes() != null
                ? place.getVisitDurationMinutes()
                : defaultDurationForGroup(place.getPlaceGroup());
        return AiTripCandidateResponse.builder()
                .id("PLACE:" + place.getId())
                .sourceType("PLACE")
                .sourceId(place.getId().toString())
                .name(place.getTitle())
                .description(trimText(place.getDescriptions(), 320))
                .address(place.getAddress())
                .lat(place.getLatitude())
                .lng(place.getLongitude())
                .rating(place.getReviewRating())
                .reviewCount(place.getReviewCount())
                .photoUrl(place.getThumbnail())
                .placeGroup(place.getPlaceGroup() != null ? place.getPlaceGroup().name() : PlaceGroup.OTHER.name())
                .category(place.getCategory())
                .visitDurationMinutes(duration)
                .durationText(formatDuration(duration))
                .build();
    }

    private AiTripCandidateResponse fromBooking(ActivityBooking booking) {
        Integer duration = booking.getVisitDurationMinutes();
        if (duration == null && booking.getDurationHours() != null) {
            duration = booking.getDurationHours().multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).intValue();
        }
        if (duration == null || duration <= 0) {
            duration = 180;
        }
        return AiTripCandidateResponse.builder()
                .id("BOOKING:" + booking.getId())
                .sourceType("BOOKING")
                .sourceId(booking.getId().toString())
                .name(booking.getTitle())
                .description(trimText(booking.getDescription(), 320))
                .address(firstNonBlank(booking.getActivityAddress(), booking.getDepartingFrom()))
                .lat(booking.getSearchLat() != null ? BigDecimal.valueOf(booking.getSearchLat()) : null)
                .lng(booking.getSearchLng() != null ? BigDecimal.valueOf(booking.getSearchLng()) : null)
                .rating(booking.getRating())
                .reviewCount(booking.getReviewCount())
                .photoUrl(booking.getThumbnail())
                .placeGroup(PlaceGroup.ATTRACTIONS.name())
                .category("activity")
                .visitDurationMinutes(duration)
                .durationText(firstNonBlank(booking.getDurationRaw(), formatDuration(duration)))
                .bookingId(booking.getId())
                .bookingSource(booking.getSource())
                .priceAmount(booking.getPriceAmount())
                .priceCurrency(booking.getPriceCurrency())
                .build();
    }

    private Integer defaultDurationForGroup(PlaceGroup group) {
        if (group == null) return 120;
        return switch (group) {
            case FOOD_AND_DRINK -> 90;
            case SHOPPING_AND_MARKET -> 120;
            case ACCOMMODATION -> 60;
            case CULTURE_AND_HERITAGE, NATURE_AND_OUTDOORS, ATTRACTIONS -> 150;
            case OTHER -> 120;
        };
    }

    private double candidateScore(AiTripCandidateResponse candidate) {
        double rating = candidate.getRating() != null ? candidate.getRating().doubleValue() : 0;
        double reviews = candidate.getReviewCount() != null ? Math.log10(candidate.getReviewCount() + 1) : 0;
        double sourceBoost = "BOOKING".equals(candidate.getSourceType()) ? 0.15 : 0;
        return rating + reviews * 0.35 + sourceBoost;
    }

    private List<AiTripCandidateResponse> rankCandidatesWithClaude(AiTripGenerateRequest request,
                                                                   List<AiTripCandidateResponse> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        String system = AI_TRIP_SYSTEM_CONTEXT + """
                You rank real travel candidates for a trip. Return strict JSON only.
                Do not invent places. Use only candidate IDs provided by the server.
                Rank using real-world fit: place type/category, opening rhythm, trip dates, likely local weather/season,
                travel distance, visit duration, user preference, rating, review volume, and whether the stop is indoor/outdoor.
                Avoid generic tourist-only lists when stronger local, food, culture, or weather-appropriate options exist.
                """ + "\n" + AiTripLanguageSupport.claudeLanguageRule();
        String prompt = """
                Trip city: %s (lat: %s, lng: %s)
                Trip dates: %s to %s (%d days)
                Pace: %s
                User preferences: %s

                Climate context: infer from city location and month.
                Vietnam north (lat>20): Nov–Mar is cool/dry, Apr–Oct is warm/rainy.
                Vietnam central (lat 12–20): Sep–Dec is rainy/flood risk, Jan–Aug is dry/hot.
                Vietnam south (lat<12): Nov–Apr is dry, May–Oct is rainy.
                Highlands (Sa Pa, Da Lat): always 5–10°C cooler than coastal cities at same latitude.

                Ranking rules (apply in order):
                1. ACCOMMODATION: rank low — exclude from top positions, place near end.
                2. FOOD_AND_DRINK: prefer stops that fit a natural meal window (breakfast ~07:30, lunch ~12:00, dinner ~18:30).
                3. NATURE_AND_OUTDOORS: rank higher if trip month has good weather; rank lower during likely rainy/hot months.
                4. Prioritize higher rating + review count when other factors are equal.
                5. Prefer variety across placeGroups — avoid clustering 4+ stops of the same group consecutively.

                Return JSON only:
                {"candidateIds":["id1","id2"],"reasons":{"id1":"concrete reason: place type + timing + weather fit"}}
                Candidates:
                %s
                """.formatted(
                request.getCityName(),
                request.getCityLat(), request.getCityLng(),
                request.getStartDate(), request.getEndDate(),
                resolveDayCount(request),
                normalizePace(request.getPace()),
                Optional.ofNullable(request.getPreferenceText()).orElse("none"),
                JsonUtils.toJson(candidates));

        return claudeClient.completeJson(system, prompt)
                .map(json -> applyRankResult(json, candidates))
                .orElse(candidates);
    }

    private List<AiTripCandidateResponse> applyRankResult(String rawJson, List<AiTripCandidateResponse> candidates) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(rawJson));
            JsonNode idsNode = root.get("candidateIds");
            JsonNode reasonsNode = root.get("reasons");
            if (idsNode == null || !idsNode.isArray()) {
                return candidates;
            }
            Map<String, AiTripCandidateResponse> byId = candidates.stream()
                    .collect(Collectors.toMap(AiTripCandidateResponse::getId, Function.identity(), (a, b) -> a));
            List<AiTripCandidateResponse> ordered = new ArrayList<>();
            for (JsonNode idNode : idsNode) {
                AiTripCandidateResponse candidate = byId.remove(idNode.asText());
                if (candidate != null) {
                    if (reasonsNode != null && reasonsNode.has(candidate.getId())) {
                        candidate.setAiReason(reasonsNode.get(candidate.getId()).asText());
                    }
                    ordered.add(candidate);
                }
            }
            ordered.addAll(byId.values());
            return ordered;
        } catch (Exception e) {
            log.warn("Failed to parse Claude candidate ranking: {}", e.getMessage());
            return candidates;
        }
    }

    private List<ScheduledCandidate> scheduleCandidatesWithClaude(AiTripDraft draft,
                                                                  List<AiTripCandidateResponse> selected) {
        if (selected.isEmpty()) {
            return List.of();
        }
        List<AiTripCandidateResponse> ordered = orderByClaudeOrDistance(draft, selected);
        return assignTimes(draft, ordered);
    }

    private List<AiTripCandidateResponse> orderByClaudeOrDistance(AiTripDraft draft,
                                                                  List<AiTripCandidateResponse> selected) {
        String system = AI_TRIP_SYSTEM_CONTEXT + """
                You order real travel candidates for an itinerary. Return strict JSON only.
                Cluster by geographic proximity using lat/lng. Use only candidate IDs provided by the server.
                Use each candidate's placeGroup, category, visitDurationMinutes, address, rating, reviewCount, price, and description.
                Build practical days that usually start around 09:00 and end around 18:00, but treat that as a soft travel rhythm, not a hard limit.
                It is acceptable to start earlier or end later when sunrise/sunset, dining time, transport, nightlife, or long travel makes that better.
                Infer likely local season/weather from city and trip dates when explicit weather is unavailable:
                keep outdoor/nature stops in better daylight windows, avoid too many exposed outdoor stops in likely hot/rainy periods,
                place indoor, food, shopping, and culture stops as good buffers.
                Put breakfast/lunch/dinner food stops into natural meal windows when selected.
                Consider realistic transport between stops and avoid zig-zag days.
                Prefer realistic transitions and avoid generic ordering.
                """ + "\n" + AiTripLanguageSupport.claudeLanguageRule();
        String prompt = """
                City: %s (lat: %s, lng: %s)
                Trip dates: %s to %s (%d days)
                Pace: %s
                Preferences: %s

                Climate context: infer from city location and month.
                Vietnam north (lat>20): Nov–Mar is cool/dry, Apr–Oct is warm/rainy.
                Vietnam central (lat 12–20): Sep–Dec is rainy/flood risk, Jan–Aug is dry/hot.
                Vietnam south (lat<12): Nov–Apr is dry, May–Oct is rainy.
                Highlands (Sa Pa, Da Lat): always 5–10°C cooler than coastal cities at same latitude.

                Ordering rules — apply strictly:
                1. Each day starts around 09:00 and ends around 18:00. RELAXED pace: max 5 stops/day. BALANCED: max 7. EAGER: max 9.
                2. FOOD_AND_DRINK must land in a meal window:
                   - First FOOD stop of the day → breakfast slot (before 09:30)
                   - Second FOOD stop → lunch slot (11:30–13:30)
                   - Third FOOD stop → dinner slot (18:00–20:30)
                   If there are more FOOD stops than meal windows in a day, push extras to the next day.
                3. ACCOMMODATION must be placed as the last stop of its day (check-in logic).
                   If multiple ACCOMMODATION stops exist, keep only the most relevant one per day.
                4. NATURE_AND_OUTDOORS: place in morning (before 11:00) or late afternoon (after 15:00).
                   Never place outdoor/nature stops at midday during hot/rainy months.
                5. Cluster geographically within each day — minimize backtracking.
                   Stops in the same district or within walking distance should be consecutive.
                6. Do not repeat the same placeGroup more than 3 times in a row.

                Return JSON only — same candidates, reordered:
                {"candidateIds":["id1","id2","id3"]}
                Candidates:
                %s
                """.formatted(
                draft.getCityName(),
                draft.getCityLat(), draft.getCityLng(),
                draft.getStartDate(), draft.getEndDate(),
                draft.getDayCount(),
                draft.getPace(),
                Optional.ofNullable(draft.getPreferenceText()).orElse("none"),
                JsonUtils.toJson(selected));
        return claudeClient.completeJson(system, prompt)
                .map(json -> parseCandidateOrder(json, selected))
                .orElseGet(() -> nearestNeighborOrder(draft, selected));
    }

    private List<AiTripCandidateResponse> parseCandidateOrder(String rawJson, List<AiTripCandidateResponse> selected) {
        try {
            JsonNode idsNode = objectMapper.readTree(extractJson(rawJson)).get("candidateIds");
            if (idsNode == null || !idsNode.isArray()) {
                return nearestNeighborOrder(null, selected);
            }
            Map<String, AiTripCandidateResponse> byId = selected.stream()
                    .collect(Collectors.toMap(AiTripCandidateResponse::getId, Function.identity(), (a, b) -> a));
            List<AiTripCandidateResponse> ordered = new ArrayList<>();
            for (JsonNode idNode : idsNode) {
                AiTripCandidateResponse candidate = byId.remove(idNode.asText());
                if (candidate != null) {
                    ordered.add(candidate);
                }
            }
            ordered.addAll(byId.values());
            return ordered;
        } catch (Exception e) {
            return nearestNeighborOrder(null, selected);
        }
    }

    private List<AiTripCandidateResponse> nearestNeighborOrder(AiTripDraft draft, List<AiTripCandidateResponse> input) {
        List<AiTripCandidateResponse> remaining = new ArrayList<>(input);
        List<AiTripCandidateResponse> ordered = new ArrayList<>();
        BigDecimal currentLat = draft != null ? draft.getCityLat() : null;
        BigDecimal currentLng = draft != null ? draft.getCityLng() : null;

        while (!remaining.isEmpty()) {
            final BigDecimal lat = currentLat;
            final BigDecimal lng = currentLng;
            AiTripCandidateResponse next = remaining.stream()
                    .min(Comparator.comparingDouble(candidate -> distanceKm(lat, lng, candidate.getLat(), candidate.getLng())))
                    .orElse(remaining.get(0));
            ordered.add(next);
            remaining.remove(next);
            currentLat = next.getLat();
            currentLng = next.getLng();
        }
        return ordered;
    }

    private List<ScheduledCandidate> assignTimes(AiTripDraft draft, List<AiTripCandidateResponse> ordered) {
        int capMinutes = switch (draft.getPace()) {
            case "RELAXED" -> 7 * 60;
            case "EAGER" -> 11 * 60;
            default -> 9 * 60;
        };
        int day = 1;
        int used = 0;
        LocalTime cursor = LocalTime.of(9, 0);
        List<ScheduledCandidate> schedule = new ArrayList<>();
        AiTripCandidateResponse previous = null;

        for (AiTripCandidateResponse candidate : ordered) {
            if (day > draft.getDayCount()) {
                break;
            }
            int duration = clamp(candidate.getVisitDurationMinutes() != null ? candidate.getVisitDurationMinutes() : 120, 45, 480);
            int travelGap = previous == null ? 0 : travelGapMinutes(previous, candidate);
            int mealGap = needsMealBreak(cursor.plusMinutes(travelGap), duration) ? 60 : 0;
            int block = duration + travelGap + mealGap;
            if (used > 0 && used + block > capMinutes) {
                day++;
                used = 0;
                cursor = LocalTime.of(9, 0);
                previous = null;
                travelGap = 0;
                mealGap = needsMealBreak(cursor, duration) ? 60 : 0;
                block = duration + mealGap;
            }
            if (day > draft.getDayCount()) {
                break;
            }
            if (used > 0) {
                cursor = cursor.plusMinutes(travelGap);
            }
            if (mealGap > 0) {
                cursor = cursor.plusMinutes(mealGap);
            }
            LocalTime start = cursor;
            LocalTime end = start.plusMinutes(duration);
            schedule.add(new ScheduledCandidate(candidate, day, start, end, recommendTransport(previous, candidate)));
            used += block;
            cursor = end;
            previous = candidate;
        }
        return schedule;
    }

    private int travelGapMinutes(AiTripCandidateResponse from, AiTripCandidateResponse to) {
        if (from.getLat() == null || from.getLng() == null || to.getLat() == null || to.getLng() == null) {
            return 30;
        }
        double km = haversineKm(
                from.getLat().doubleValue(),
                from.getLng().doubleValue(),
                to.getLat().doubleValue(),
                to.getLng().doubleValue()
        );
        if (km < 1.0) return 15;
        if (km < 3.0) return 25;
        if (km < 8.0) return 40;
        if (km < 20.0) return 60;
        return 90;
    }

    private boolean needsMealBreak(LocalTime start, int durationMinutes) {
        LocalTime end = start.plusMinutes(durationMinutes);
        return start.isBefore(LocalTime.of(13, 0)) && end.isAfter(LocalTime.of(12, 0));
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double radiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return radiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private Activity toActivity(UUID tripId, ScheduledCandidate item, UUID userId, Map<String, String> visitTips) {
        AiTripCandidateResponse candidate = item.candidate();
        String visitTip = visitTips.getOrDefault(candidate.getId(), fallbackVisitTip(candidate, item));
        return Activity.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .dayNumber(item.dayNumber())
                .placeId("PLACE".equals(candidate.getSourceType()) ? candidate.getSourceId() : null)
                .name(candidate.getName())
                .address(candidate.getAddress())
                .lat(candidate.getLat())
                .lng(candidate.getLng())
                .startTime(item.startTime())
                .endTime(item.endTime())
                .category(activityCategoryFor(candidate))
                .transportMode(item.transportMode())
                .rating(candidate.getRating())
                .photoUrl(candidate.getPhotoUrl())
                .description(trimText(visitTip, 280))
                .status(ActivityStatus.CONFIRMED)
                .addedBy(userId)
                .isAccommodation(PlaceGroup.ACCOMMODATION.name().equals(candidate.getPlaceGroup()))
                .isStartingPoint(false)
                .bookingId(candidate.getBookingId())
                .bookingSource(candidate.getBookingSource())
                .build();
    }

    private String activityCategoryFor(AiTripCandidateResponse candidate) {
        String category = normalizeKey(candidate.getCategory());
        String name = normalizeKey(candidate.getName());
        String text = category + " " + name + " " + normalizeKey(candidate.getDescription());
        if (containsAny(text, "restaurant", "food", "cafe", "coffee", "bar", "pub", "bakery", "drink", "quan", "nha hang")) {
            return "restaurant";
        }
        if (containsAny(text, "hotel", "hostel", "resort", "homestay", "accommodation")) {
            return "hotel";
        }
        if (containsAny(text, "beach", "island", "bay", "coast", "bien", "dao")) {
            return "beach";
        }
        if (containsAny(text, "temple", "pagoda", "church", "cathedral", "shrine", "chua", "den", "nha tho")) {
            return "spiritual";
        }
        if (containsAny(text, "park", "garden", "mountain", "lake", "waterfall", "nature", "trail", "forest", "vuon", "ho", "thac")) {
            return "nature";
        }
        if (containsAny(text, "market", "mall", "shopping", "shop", "cho")) {
            return "shopping";
        }
        if (containsAny(text, "museum", "gallery", "heritage", "historic", "palace", "citadel", "monument", "bao tang")) {
            return "museum";
        }
        if (containsAny(text, "show", "theater", "cinema", "night", "club", "entertainment", "performance")) {
            return "entertainment";
        }
        if (candidate.getPlaceGroup() == null) {
            return "attraction";
        }
        return switch (candidate.getPlaceGroup()) {
            case "FOOD_AND_DRINK" -> "restaurant";
            case "ACCOMMODATION" -> "hotel";
            case "SHOPPING_AND_MARKET" -> "shopping";
            case "NATURE_AND_OUTDOORS" -> "nature";
            case "CULTURE_AND_HERITAGE" -> "museum";
            case "ATTRACTIONS" -> "attraction";
            default -> "attraction";
        };
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(normalizeKey(token))) {
                return true;
            }
        }
        return false;
    }

    private TransportMode recommendTransport(AiTripCandidateResponse previous, AiTripCandidateResponse current) {
        if (previous == null || previous.getLat() == null || previous.getLng() == null
                || current.getLat() == null || current.getLng() == null) {
            return null;
        }
        double km = haversineKm(
                previous.getLat().doubleValue(),
                previous.getLng().doubleValue(),
                current.getLat().doubleValue(),
                current.getLng().doubleValue()
        );
        if (km < 1.2) return TransportMode.WALKING;
        if (km < 8.0) return TransportMode.MOTORBIKE;
        if (km < 45.0) return TransportMode.CAR;
        return TransportMode.TRAIN;
    }

    private Map<String, String> generateVisitTips(AiTripDraft draft, List<ScheduledCandidate> schedule) {
        if (schedule.isEmpty()) {
            return Map.of();
        }

        List<Map<String, Object>> payload = new ArrayList<>();
        for (ScheduledCandidate item : schedule) {
            AiTripCandidateResponse candidate = item.candidate();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", candidate.getId());
            row.put("name", candidate.getName());
            row.put("placeGroup", candidate.getPlaceGroup());
            row.put("category", candidate.getCategory());
            row.put("sourceType", candidate.getSourceType());
            row.put("description", trimText(candidate.getDescription(), 120));
            row.put("address", candidate.getAddress());
            row.put("rating", candidate.getRating());
            row.put("dayNumber", item.dayNumber());
            row.put("startTime", item.startTime().toString());
            row.put("endTime", item.endTime().toString());
            row.put("transportModeToHere", item.transportMode() != null ? item.transportMode().name() : null);
            payload.add(row);
        }

        String system = AI_TRIP_SYSTEM_CONTEXT + """
                You write very short practical visit tips for a travel itinerary.
                Return strict JSON only: {"tips":{"candidateId":"short tip text"}}.
                Each tip: 1 short sentence (max 160 chars) on HOW to visit or enjoy the place.
                Use the scheduled time, place type/category, likely weather/season from city and trip dates, and transport context.
                Do NOT copy marketing or listing descriptions. Use only candidate IDs from the input.
                """ + "\n" + AiTripLanguageSupport.claudeLanguageRule();
        String prompt = """
                City: %s
                Trip dates: %s to %s
                Pace: %s

                User preferences: %s

                Climate context: infer from city location and month.
                Vietnam north (lat>20): Nov-Mar is cool/dry, Apr-Oct is warm/rainy.
                Vietnam central (lat 12-20): Sep-Dec is rainy/flood risk, Jan-Aug is dry/hot.
                Vietnam south (lat<12): Nov-Apr is dry, May-Oct is rainy.
                Highlands (Sa Pa, Da Lat): always 5-10 C cooler than coastal cities at same latitude.

                Write one practical tip per activity. Rules:
                - Max 120 characters per tip.
                - Be specific to the place - use name, category, and description as context.
                - Focus on HOW to visit: best time to arrive, what to bring, what to avoid, local tip.
                - Use scheduled startTime to make timing-relevant tips (e.g. "arrive early before crowds" for an 08:00 slot).
                - For FOOD_AND_DRINK: mention a dish or ordering tip if inferable from description.
                - For NATURE_AND_OUTDOORS: mention weather/clothing/sun protection relevant to the trip month.
                - For ACCOMMODATION: mention check-in tip or nearby amenity.
                - Never use generic phrases like "enjoy your visit" or "have a great time".
                - Write in the same language as the user preferences field. If preferences is "none", use English.

                Return JSON only:
                {"tips":{"candidateId":"tip text"}}
                Activities:
                %s
                """.formatted(
                draft.getCityName(),
                draft.getStartDate(), draft.getEndDate(),
                draft.getPace(),
                Optional.ofNullable(draft.getPreferenceText()).orElse("none"),
                JsonUtils.toJson(payload));

        Map<String, String> tips = claudeClient.completeJson(system, prompt)
                .map(this::parseVisitTips)
                .orElseGet(Map::of);

        Map<String, String> resolved = new HashMap<>();
        for (ScheduledCandidate item : schedule) {
            AiTripCandidateResponse candidate = item.candidate();
            String tip = tips.get(candidate.getId());
            if (tip == null || tip.isBlank()) {
                tip = fallbackVisitTip(candidate, item);
            }
            resolved.put(candidate.getId(), trimText(tip, 280));
        }
        return resolved;
    }

    private Map<String, String> parseVisitTips(String rawJson) {
        try {
            JsonNode tipsNode = objectMapper.readTree(extractJson(rawJson)).get("tips");
            if (tipsNode == null || !tipsNode.isObject()) {
                return Map.of();
            }
            Map<String, String> tips = new HashMap<>();
            tipsNode.fields().forEachRemaining(entry -> {
                String value = entry.getValue().asText("").trim();
                if (!value.isBlank()) {
                    tips.put(entry.getKey(), value);
                }
            });
            return tips;
        } catch (Exception e) {
            log.warn("Failed to parse Claude visit tips: {}", e.getMessage());
            return Map.of();
        }
    }

    private String fallbackVisitTip(AiTripCandidateResponse candidate, ScheduledCandidate item) {
        if (candidate.getAiReason() != null && !candidate.getAiReason().isBlank()) {
            return trimText(candidate.getAiReason(), 200);
        }
        String group = candidate.getPlaceGroup() != null ? candidate.getPlaceGroup() : PlaceGroup.OTHER.name();
        String lang = AiTripLanguageSupport.currentCode();
        String timeHint = AiTripLanguageSupport.timeHint(item.startTime().getHour(), lang);
        return AiTripLanguageSupport.fallbackVisitTip(
                group,
                candidate.getSourceType(),
                timeHint);
    }

    private String buildGenerationSummary(AiTripDraft draft,
                                          int selectedCount,
                                          List<ScheduledCandidate> schedule,
                                          List<AiTripCandidateResponse> skipped,
                                          int filledDays) {
        return AiTripGenerationSummary.build(
                draft,
                selectedCount,
                schedule.size(),
                skipped,
                filledDays);
    }

    private AiTripConfirmResponse completedResponse(AiTripDraft draft) {
        TripResponse trip = TripResponse.builder()
                .id(draft.getCreatedTripId())
                .name(draft.getTripName())
                .destination(draft.getCityName())
                .lat(draft.getCityLat())
                .lng(draft.getCityLng())
                .startDate(draft.getStartDate())
                .endDate(draft.getEndDate())
                .build();
        return AiTripConfirmResponse.builder()
                .trip(trip)
                .selectedCount(0)
                .scheduledCount(0)
                .filledDays(0)
                .totalDays(draft.getDayCount())
                .coverageMessage(AiTripLanguageSupport.tripAlreadyCreatedMessage())
                .build();
    }

    private List<AiTripCandidateResponse> parseCandidates(String json) {
        List<AiTripCandidateResponse> candidates = JsonUtils.fromJson(json,
                new TypeReference<List<AiTripCandidateResponse>>() {
                });
        return candidates != null ? candidates : List.of();
    }

    private String buildCoverageMessage(int selectedCount, int scheduledCount, int filledDays, int totalDays) {
        return AiTripLanguageSupport.coverageMessage(selectedCount, scheduledCount, filledDays, totalDays);
    }

    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    private String trimText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 3) + "...";
    }

    private String formatDuration(Integer minutes) {
        if (minutes == null || minutes <= 0) return null;
        int hours = minutes / 60;
        int mins = minutes % 60;
        if (hours == 0) return mins + "m";
        if (mins == 0) return hours + "h";
        return hours + "h " + mins + "m";
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String normalizeKey(String value) {
        if (value == null) return "";
        String noMarks = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noMarks.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private double distanceKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return Double.MAX_VALUE / 4;
        }
        double r = 6371;
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLng = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue()))
                * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private record ScheduledCandidate(AiTripCandidateResponse candidate, int dayNumber,
                                      LocalTime startTime, LocalTime endTime,
                                      TransportMode transportMode) {
    }
}
