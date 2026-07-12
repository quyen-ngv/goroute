package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.CreateActivityPlaceImportJobRequest;
import com.ds.goroute.dto.request.CreateManualPlaceImportJobRequest;
import com.ds.goroute.dto.request.CreateSocialPlaceImportJobRequest;
import com.ds.goroute.dto.response.PlaceImportJobItemResponse;
import com.ds.goroute.dto.response.PlaceImportJobResponse;
import com.ds.goroute.dto.response.AdminPlaceImportMappingResponse;
import com.ds.goroute.dto.response.AdminPlaceImportRunResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceImportJob;
import com.ds.goroute.entity.PlaceImportJobItem;
import com.ds.goroute.entity.SocialLocationJob;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.mapper.PlaceImportJobMapper;
import com.ds.goroute.mapper.SocialLocationJobMapper;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.PlaceImportJobService;
import com.ds.goroute.thirdparty.scrape.ScrapeJobStatusResponse;
import com.ds.goroute.thirdparty.scrape.ScrapeJobTriggerResponse;
import com.ds.goroute.thirdparty.scrape.ScrapePlaceImportJobRequest;
import com.ds.goroute.thirdparty.scrape.ScrapePlaceSearchResponse;
import com.ds.goroute.thirdparty.scrape.ScrapeResolveResponse;
import com.ds.goroute.thirdparty.scrape.ScrapeServiceClient;
import com.ds.goroute.type.PlaceImportJobItemStatus;
import com.ds.goroute.type.PlaceImportApprovalStatus;
import com.ds.goroute.type.PlaceImportJobStatus;
import com.ds.goroute.type.PlaceImportSourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.springframework.transaction.annotation.Transactional;
import com.ds.goroute.util.PlaceImportCandidateKey;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceImportJobServiceImpl implements PlaceImportJobService {

    private static final int DEFAULT_MAX_REVIEWS = 5;
    private static final int MAX_POLL_ATTEMPTS = 300;
    private static final BigDecimal DUPLICATE_DISTANCE_METERS = BigDecimal.valueOf(25);

    private final PlaceImportJobMapper jobMapper;
    private final SocialLocationJobMapper socialLocationJobMapper;
    private final PlaceRepository placeRepository;
    private final ActivityRepository activityRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final ScrapeServiceClient scrapeServiceClient;
    private final ObjectMapper objectMapper;

    @Resource(name = "placeImportJobExecutor")
    private Executor placeImportJobExecutor;

    @Resource(name = "placeImportAdminExecutor")
    private Executor placeImportAdminExecutor;

    @Value("${goroute.internal.public-base-url:http://goroute-app:8080}")
    private String publicBaseUrl;

    @Override
    public PlaceImportJobResponse createFromSocialJobs(UUID userId, CreateSocialPlaceImportJobRequest request) {
        PlaceImportJob job = createJob(userId, PlaceImportSourceType.SOCIAL_LOCATION, null, safeMaxReviews(request.getMaxReviews()), request);
        placeImportJobExecutor.execute(() -> runSocialJob(job.getId(), userId, request));
        return toResponse(job, List.of());
    }

    @Override
    public PlaceImportJobResponse createFromActivities(UUID userId, CreateActivityPlaceImportJobRequest request) {
        PlaceImportJob job = createJob(userId, PlaceImportSourceType.ACTIVITY, request.getTripId(), DEFAULT_MAX_REVIEWS, request);
        placeImportJobExecutor.execute(() -> runActivityJob(job.getId(), userId, request));
        return toResponse(job, List.of());
    }

    @Override
    public PlaceImportJobResponse get(UUID userId, UUID jobId) {
        PlaceImportJob job = requireOwnedJob(userId, jobId);
        return toResponse(job, jobMapper.findItemsByJobId(jobId));
    }

    @Override
    public List<PlaceImportJobResponse> listMine(UUID userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return jobMapper.findJobsByUserId(userId, safeSize, safePage * safeSize).stream()
                .map(job -> toResponse(job, null))
                .toList();
    }

    @Override
    public AdminPlaceImportRunResponse adminRunSocialJobs(CreateSocialPlaceImportJobRequest request) {
        if (Boolean.TRUE.equals(request.getAllUsers())) {
            if (request.getUserId() != null) {
                throw new IllegalArgumentException("userId cannot be used with allUsers=true");
            }
            if (request.getSocialJobIds() != null && !request.getSocialJobIds().isEmpty()) {
                throw new IllegalArgumentException("socialJobIds cannot be used with allUsers=true");
            }
        }
        List<UUID> userIds = targetUserIds(request.getUserId(), request.getAllUsers());
        placeImportAdminExecutor.execute(() -> userIds.forEach(userId -> {
            try {
                scheduleSocialImportForAdmin(userId, request);
            } catch (Exception e) {
                log.error("Admin social place import could not be scheduled for user={}", userId, e);
            }
        }));
        return AdminPlaceImportRunResponse.builder()
                .sourceType(PlaceImportSourceType.SOCIAL_LOCATION)
                .targetedUserCount(userIds.size())
                .queued(true)
                .build();
    }

    @Override
    public AdminPlaceImportRunResponse adminRunActivityJobs(CreateActivityPlaceImportJobRequest request) {
        if (Boolean.TRUE.equals(request.getAllUsers())) {
            if (request.getUserId() != null) {
                throw new IllegalArgumentException("userId cannot be used with allUsers=true");
            }
            if (request.getTripId() != null) {
                throw new IllegalArgumentException("tripId cannot be used with allUsers=true");
            }
        }
        List<UUID> userIds = targetUserIds(request.getUserId(), request.getAllUsers());
        placeImportAdminExecutor.execute(() -> userIds.forEach(userId -> {
            try {
                scheduleActivityImportForAdmin(userId, request);
            } catch (Exception e) {
                log.error("Admin activity place import could not be scheduled for user={}", userId, e);
            }
        }));
        return AdminPlaceImportRunResponse.builder()
                .sourceType(PlaceImportSourceType.ACTIVITY)
                .targetedUserCount(userIds.size())
                .queued(true)
                .build();
    }

    @Override
    public PlaceImportJobResponse adminRunManualPlaceImport(CreateManualPlaceImportJobRequest request) {
        Activity activity = activityRepository.findById(request.getActivityId())
                .orElseThrow(() -> new IllegalArgumentException("Activity not found"));
        if (hasExistingPlace(activity)) {
            throw new IllegalArgumentException("Activity already has a mapped place");
        }
        Trip trip = tripRepository.findById(activity.getTripId())
                .orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        Candidate candidate = Candidate.fromManualLink(activity, request.getGoogleMapsUrl().trim());
        candidate.sourceCandidateKey = candidateKey(candidate);
        PlaceImportJob job = createJob(
                trip.getOwnerId(),
                PlaceImportSourceType.MANUAL,
                activity.getTripId(),
                safeMaxReviews(request.getMaxReviews()),
                request);
        placeImportJobExecutor.execute(() -> runJob(job.getId(), List.of(candidate)));
        return toResponse(job, List.of());
    }

    @Override
    public List<PlaceImportJobResponse> adminListJobs(UUID userId, String status, int page, int size) {
        String normalizedStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                normalizedStatus = PlaceImportJobStatus.valueOf(status.trim().toUpperCase()).name();
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid place import job status");
            }
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return jobMapper.findAdminJobs(userId, normalizedStatus, safeSize, safePage * safeSize)
                .stream()
                .map(job -> toResponse(job, null))
                .toList();
    }

    @Override
    public PlaceImportJobResponse adminGetJob(UUID jobId) {
        PlaceImportJob job = jobMapper.findJobById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Place import job not found");
        }
        return toResponse(job, jobMapper.findItemsByJobId(jobId));
    }

    @Override
    public List<AdminPlaceImportMappingResponse> adminListMappings(String approvalStatus, int page, int size) {
        String normalizedStatus = approvalStatus == null ? "ALL" : approvalStatus.trim().toUpperCase();
        if (!"ALL".equals(normalizedStatus)) {
            try {
                normalizedStatus = PlaceImportApprovalStatus.valueOf(normalizedStatus).name();
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid approval status");
            }
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return jobMapper.findAdminMappings("ALL".equals(normalizedStatus) ? null : normalizedStatus,
                safeSize, safePage * safeSize);
    }

    @Override
    @Transactional
    public AdminPlaceImportMappingResponse adminApproveMapping(UUID itemId, String note) {
        PlaceImportJobItem item = jobMapper.findItemById(itemId);
        if (item == null) {
            throw new IllegalArgumentException("Place import mapping not found");
        }
        if (item.getApprovalStatus() != PlaceImportApprovalStatus.PENDING) {
            throw new IllegalArgumentException("Place import mapping has already been moderated");
        }

        UUID placeId = item.getImportedPlaceId() != null ? item.getImportedPlaceId() : item.getExistingPlaceId();
        if (placeId == null) {
            throw new IllegalArgumentException("Place import mapping has no resolved place");
        }
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new IllegalArgumentException("Mapped place not found"));
        applyApprovedActivityMapping(item, place);

        item.setApprovalStatus(PlaceImportApprovalStatus.APPROVED);
        item.setApprovalNote(trimToNull(note));
        item.setApprovedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateItem(item);
        return jobMapper.findAdminMappingByItemId(itemId);
    }

    @Override
    @Transactional
    public void adminRejectMapping(UUID itemId, String note) {
        PlaceImportJobItem item = jobMapper.findItemById(itemId);
        if (item == null) {
            throw new IllegalArgumentException("Place import mapping not found");
        }
        if (item.getApprovalStatus() != PlaceImportApprovalStatus.PENDING) {
            throw new IllegalArgumentException("Place import mapping has already been moderated");
        }
        item.setApprovalStatus(PlaceImportApprovalStatus.REJECTED);
        item.setApprovalNote(trimToNull(note));
        item.setApprovedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateItem(item);
    }

    private void runSocialJob(UUID jobId, UUID userId, CreateSocialPlaceImportJobRequest request) {
        try {
            runJob(jobId, collectSocialCandidates(userId, request));
        } catch (Exception e) {
            failBeforeProcessing(jobId, e);
        }
    }

    private void runActivityJob(UUID jobId, UUID userId, CreateActivityPlaceImportJobRequest request) {
        try {
            runJob(jobId, collectActivityCandidates(userId, request));
        } catch (Exception e) {
            failBeforeProcessing(jobId, e);
        }
    }

    private void scheduleSocialImportForAdmin(UUID userId, CreateSocialPlaceImportJobRequest request) {
        List<Candidate> candidates = collectSocialCandidates(userId, request);
        if (candidates.isEmpty()) {
            return;
        }
        PlaceImportJob job = createJob(
                userId,
                PlaceImportSourceType.SOCIAL_LOCATION,
                null,
                safeMaxReviews(request.getMaxReviews()),
                request);
        placeImportJobExecutor.execute(() -> runJob(job.getId(), candidates));
    }

    private void scheduleActivityImportForAdmin(UUID userId, CreateActivityPlaceImportJobRequest request) {
        List<Candidate> candidates = collectActivityCandidates(userId, request);
        if (candidates.isEmpty()) {
            return;
        }
        PlaceImportJob job = createJob(
                userId,
                PlaceImportSourceType.ACTIVITY,
                request.getTripId(),
                DEFAULT_MAX_REVIEWS,
                request);
        placeImportJobExecutor.execute(() -> runJob(job.getId(), candidates));
    }

    private void runJob(UUID jobId, List<Candidate> candidates) {
        PlaceImportJob job = jobMapper.findJobById(jobId);
        startJob(job, candidates.size());
        try {
            for (Candidate candidate : candidates) {
                PlaceImportJobItem item = createItem(jobId, candidate);
                if (item == null) {
                    continue;
                }
                processItem(job, item, candidate);
                refreshCounts(job);
            }
            finishJob(job, null);
        } catch (Exception e) {
            log.error("Place import job failed: {}", jobId, e);
            finishJob(job, e.getMessage());
        }
    }

    private void processItem(PlaceImportJob job, PlaceImportJobItem item, Candidate candidate) {
        try {
            if (candidate.requiresSearch && !selectFirstSearchCandidate(candidate)) {
                failItem(item, "No Google Maps place candidate found for: " + candidate.searchQuery);
                return;
            }
            enrichFromResolve(candidate);
            applyCandidateToItem(item, candidate);
            Place existingPlace = findExistingPlace(candidate);
            if (existingPlace != null) {
                item.setExistingPlaceId(existingPlace.getId());
                item.setImportedPlaceId(existingPlace.getId());
                item.setStatus(PlaceImportJobItemStatus.SKIPPED_EXISTING);
                item.setApprovalStatus(initialApprovalStatus(job));
                item.setUpdatedAt(LocalDateTime.now());
                jobMapper.updateItem(item);
                applyManualActivityMapping(job, item, existingPlace);
                return;
            }

            if (candidate.url == null || candidate.url.isBlank()) {
                failItem(item, "Missing Google Maps URL");
                return;
            }

            ScrapeJobTriggerResponse trigger = scrapeServiceClient.triggerPlaceScrapeImport(
                    ScrapePlaceImportJobRequest.builder()
                            .url(candidate.url)
                            .maxReviews(job.getSourceType() == PlaceImportSourceType.ACTIVITY
                                    ? DEFAULT_MAX_REVIEWS
                                    : job.getMaxReviews())
                            .maxScrolls(100)
                            .headless(true)
                            .visibilityStatus("INACTIVE")
                            .importConfig(ScrapePlaceImportJobRequest.ImportConfig.builder()
                                    .enabled(true)
                                    .url(publicBaseUrl.replaceAll("/+$", "") + "/v1/api/places/import")
                                    .method("POST")
                                    .headers(Map.of())
                                    .build())
                            .build());

            if (trigger == null || trigger.getJobId() == null || trigger.getJobId().isBlank()) {
                failItem(item, "Python scrape/import trigger failed");
                return;
            }

            item.setPythonJobId(trigger.getJobId());
            item.setStatus(PlaceImportJobItemStatus.PROCESSING);
            item.setUpdatedAt(LocalDateTime.now());
            jobMapper.updateItem(item);

            ScrapeJobStatusResponse completed = pollPythonJob(trigger.getJobId());
            if (completed == null || !"completed".equalsIgnoreCase(completed.getStatus())) {
                String error = completed != null && completed.getError() != null
                        ? completed.getError().getMessage()
                        : "Python scrape/import job did not complete";
                failItem(item, error);
                return;
            }

            UUID importedPlaceId = resolveImportedPlaceId(completed, candidate);
            if (importedPlaceId == null) {
                failItem(item, "Imported place id could not be resolved");
                return;
            }
            item.setImportedPlaceId(importedPlaceId);
            item.setStatus(PlaceImportJobItemStatus.COMPLETED);
            item.setApprovalStatus(initialApprovalStatus(job));
            item.setUpdatedAt(LocalDateTime.now());
            jobMapper.updateItem(item);
            Place importedPlace = placeRepository.findById(importedPlaceId)
                    .orElseThrow(() -> new IllegalArgumentException("Imported place not found"));
            applyManualActivityMapping(job, item, importedPlace);
        } catch (Exception e) {
            log.warn("Place import item failed: job={} item={} error={}", job.getId(), item.getId(), e.getMessage());
            failItem(item, e.getMessage());
        }
    }

    private List<Candidate> collectSocialCandidates(UUID userId, CreateSocialPlaceImportJobRequest request) {
        int limit = safeLimit(request.getLimit(), 50, 100);
        List<SocialLocationJob> socialJobs = socialLocationJobMapper.findCompletedByUserId(
                userId,
                request.getSocialJobIds(),
                Math.max(limit, 20));
        List<Candidate> candidates = new ArrayList<>();
        for (SocialLocationJob socialJob : socialJobs) {
            JsonNode root = readJson(socialJob.getResultPayload());
            JsonNode nodes = root.path("extraction").path("candidates");
            if (!nodes.isArray()) {
                continue;
            }
            for (JsonNode candidateNode : nodes) {
                JsonNode mapCandidates = candidateNode.path("mapSearch").path("candidates");
                if (!mapCandidates.isArray()) {
                    continue;
                }
                for (JsonNode mapNode : mapCandidates) {
                    Candidate candidate = Candidate.fromMapSearch(socialJob.getId(), mapNode);
                    candidate.sourceOriginalUrl = socialJob.getSourceUrl();
                    candidate.sourceCandidateKey = candidateKey(candidate);
                    if (jobMapper.existsSocialItem(socialJob.getId(), candidate.sourceCandidateKey)) {
                        continue;
                    }
                    candidates.add(candidate);
                    if (candidates.size() >= limit) {
                        return candidates;
                    }
                }
            }
        }
        return candidates;
    }

    private List<Candidate> collectActivityCandidates(UUID userId, CreateActivityPlaceImportJobRequest request) {
        int limit = safeLimit(request.getLimit(), 100, 200);
        List<Trip> trips = tripRepository.findByUserId(userId);
        if (request.getTripId() != null) {
            boolean hasAccess = trips.stream().anyMatch(trip -> trip.getId().equals(request.getTripId()));
            if (!hasAccess) {
                throw new IllegalArgumentException("Trip not found");
            }
            trips = trips.stream().filter(trip -> trip.getId().equals(request.getTripId())).toList();
        }

        List<Candidate> candidates = new ArrayList<>();
        int loadedActivities = 0;
        int skippedHasPlace = 0;
        int skippedMissingNameOrAddress = 0;
        int skippedAlreadyImported = 0;
        userTripLoop:
        for (Trip trip : trips) {
            for (Activity activity : activityRepository.findByTripId(trip.getId())) {
                loadedActivities++;
                if (hasExistingPlace(activity)) {
                    skippedHasPlace++;
                    continue;
                }
                if (isBlank(activity.getName()) || isBlank(activity.getAddress())) {
                    skippedMissingNameOrAddress++;
                    continue;
                }
                if (jobMapper.existsActivityItem(activity.getId())) {
                    skippedAlreadyImported++;
                    continue;
                }
                Candidate candidate = Candidate.fromActivity(activity);
                candidate.sourceCandidateKey = candidateKey(candidate);
                candidates.add(candidate);
                if (candidates.size() >= limit) {
                    break userTripLoop;
                }
            }
        }
        log.info("Activity place import scan: user={} trips={} loaded={} eligible={} skippedHasPlace={} "
                        + "skippedMissingNameOrAddress={} skippedAlreadyImported={}",
                userId,
                trips.size(),
                loadedActivities,
                candidates.size(),
                skippedHasPlace,
                skippedMissingNameOrAddress,
                skippedAlreadyImported);
        return candidates;
    }

    private Place findExistingPlace(Candidate candidate) {
        if (!isBlank(candidate.googlePlaceId)) {
            Place place = placeRepository.findByPlaceId(candidate.googlePlaceId);
            if (place != null) {
                return place;
            }
        }
        if (!isBlank(candidate.cid)) {
            Place place = placeRepository.findByCid(candidate.cid);
            if (place != null) {
                return place;
            }
        }
        if (candidate.latitude != null && candidate.longitude != null) {
            return placeRepository.findNearCoordinates(candidate.latitude, candidate.longitude, DUPLICATE_DISTANCE_METERS);
        }
        return null;
    }

    private void enrichFromResolve(Candidate candidate) {
        if ((!isBlank(candidate.googlePlaceId) && !isBlank(candidate.cid) && candidate.latitude != null && candidate.longitude != null)
                || isBlank(candidate.url)) {
            return;
        }
        ScrapeResolveResponse resolved = scrapeServiceClient.resolveUrl(candidate.url);
        if (resolved == null) {
            return;
        }
        candidate.googlePlaceId = firstNonBlank(candidate.googlePlaceId, resolved.getGooglePlaceId());
        candidate.cid = firstNonBlank(candidate.cid, resolved.getCid());
        candidate.latitude = candidate.latitude != null ? candidate.latitude : resolved.getLatitude();
        candidate.longitude = candidate.longitude != null ? candidate.longitude : resolved.getLongitude();
        // Resolve is used only to detect duplicates. Preserve the selected input URL
        // for the scrape so this follows the same detail flow as Telegram.
        candidate.name = firstNonBlank(candidate.name, resolved.getTitle());
    }

    private boolean selectFirstSearchCandidate(Candidate candidate) {
        ScrapePlaceSearchResponse result = scrapeServiceClient.searchPlaces(candidate.searchQuery, 1);
        if (result == null || result.getCandidates() == null || result.getCandidates().isEmpty()) {
            return false;
        }
        ScrapePlaceSearchResponse.Candidate first = result.getCandidates().getFirst();
        String url = firstNonBlank(first.getGoogleMapsLink(), first.getResolvedUrl());
        if (isBlank(url)) {
            return false;
        }
        candidate.url = url;
        candidate.name = firstNonBlank(first.getTitle(), candidate.name);
        candidate.googlePlaceId = firstNonBlank(first.getPlaceId(), candidate.googlePlaceId);
        candidate.cid = firstNonBlank(first.getCid(), candidate.cid);
        candidate.latitude = first.getLatitude();
        candidate.longitude = first.getLongitude();
        candidate.requiresSearch = false;
        return true;
    }

    private UUID resolveImportedPlaceId(ScrapeJobStatusResponse completed, Candidate candidate) {
        if (completed.getResult() != null) {
            candidate.googlePlaceId = firstNonBlank(candidate.googlePlaceId, completed.getResult().getGooglePlaceId());
        }
        String goroutePlaceId = completed.getResult() != null ? completed.getResult().getGoroutePlaceId() : null;
        if (!isBlank(goroutePlaceId)) {
            try {
                return UUID.fromString(goroutePlaceId);
            } catch (IllegalArgumentException ignored) {
                log.warn("Invalid goroutePlaceId from Python: {}", goroutePlaceId);
            }
        }
        Place existing = findExistingPlace(candidate);
        return existing != null ? existing.getId() : null;
    }

    private ScrapeJobStatusResponse pollPythonJob(String pythonJobId) throws InterruptedException {
        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            ScrapeJobStatusResponse status = scrapeServiceClient.pollJob(pythonJobId);
            if (status != null
                    && ("completed".equalsIgnoreCase(status.getStatus()) || "failed".equalsIgnoreCase(status.getStatus()))) {
                return status;
            }
            Thread.sleep(2_000L);
        }
        return null;
    }

    private void applyApprovedActivityMapping(PlaceImportJobItem item, Place place) {
        if (item.getActivityId() == null) {
            return;
        }
        Activity activity = activityRepository.findById(item.getActivityId())
                .orElseThrow(() -> new IllegalArgumentException("Activity not found"));
        String mappedPlaceId = place.getId().toString();
        if (hasExistingPlace(activity) && !mappedPlaceId.equals(activity.getPlaceId())) {
            throw new IllegalArgumentException("Activity was updated while this mapping was pending approval");
        }
        activity.setPlaceId(mappedPlaceId);
        if (place.getLatitude() != null) {
            activity.setLat(place.getLatitude());
        } else if (item.getLatitude() != null) {
            activity.setLat(item.getLatitude());
        }
        if (place.getLongitude() != null) {
            activity.setLng(place.getLongitude());
        } else if (item.getLongitude() != null) {
            activity.setLng(item.getLongitude());
        }
        activityRepository.update(activity);
    }

    private boolean hasExistingPlace(Activity activity) {
        if (isBlank(activity.getPlaceId())) {
            return false;
        }
        try {
            return placeRepository.findById(UUID.fromString(activity.getPlaceId())).isPresent();
        } catch (IllegalArgumentException ignored) {
            // Legacy Google place ids are still eligible for resolving/import.
            return false;
        }
    }

    private PlaceImportJob createJob(UUID userId, PlaceImportSourceType sourceType, UUID sourceRefId, int maxReviews, Object request) {
        LocalDateTime now = LocalDateTime.now();
        PlaceImportJob job = PlaceImportJob.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .sourceType(sourceType)
                .sourceRefId(sourceRefId)
                .status(PlaceImportJobStatus.QUEUED)
                .maxReviews(maxReviews)
                .totalItems(0)
                .skippedExistingCount(0)
                .triggeredCount(0)
                .completedCount(0)
                .failedCount(0)
                .requestPayload(toJson(request))
                .createdAt(now)
                .updatedAt(now)
                .build();
        jobMapper.insertJob(job);
        return job;
    }

    private PlaceImportJobItem createItem(UUID jobId, Candidate candidate) {
        LocalDateTime now = LocalDateTime.now();
        PlaceImportJobItem item = PlaceImportJobItem.builder()
                .id(UUID.randomUUID())
                .jobId(jobId)
                .sourceRefId(candidate.sourceRefId)
                .activityId(candidate.activityId)
                .sourceCandidateKey(candidate.sourceCandidateKey)
                .status(PlaceImportJobItemStatus.QUEUED)
                .approvalStatus(PlaceImportApprovalStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
        applyCandidateToItem(item, candidate);
        jobMapper.insertItem(item);
        return item;
    }

    private void applyCandidateToItem(PlaceImportJobItem item, Candidate candidate) {
        item.setSourceUrl(candidate.url);
        item.setSourceAddress(candidate.sourceAddress);
        item.setSourceOriginalUrl(candidate.sourceOriginalUrl);
        item.setSourceCandidateKey(candidate.sourceCandidateKey == null ? candidateKey(candidate) : candidate.sourceCandidateKey);
        item.setCandidateName(candidate.name);
        item.setGooglePlaceId(candidate.googlePlaceId);
        item.setCid(candidate.cid);
        item.setLatitude(candidate.latitude);
        item.setLongitude(candidate.longitude);
    }

    private void startJob(PlaceImportJob job, int totalItems) {
        job.setStatus(PlaceImportJobStatus.PROCESSING);
        job.setTotalItems(totalItems);
        job.setStartedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateJob(job);
    }

    private void finishJob(PlaceImportJob job, String errorMessage) {
        refreshCounts(job);
        job.setStatus(errorMessage == null ? PlaceImportJobStatus.COMPLETED : PlaceImportJobStatus.FAILED);
        job.setErrorMessage(errorMessage);
        job.setCompletedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateJob(job);
    }

    private void refreshCounts(PlaceImportJob job) {
        List<PlaceImportJobItem> items = jobMapper.findItemsByJobId(job.getId());
        job.setSkippedExistingCount((int) items.stream().filter(item -> item.getStatus() == PlaceImportJobItemStatus.SKIPPED_EXISTING).count());
        job.setTriggeredCount((int) items.stream().filter(item -> item.getPythonJobId() != null && !item.getPythonJobId().isBlank()).count());
        job.setCompletedCount((int) items.stream().filter(item -> item.getStatus() == PlaceImportJobItemStatus.COMPLETED).count());
        job.setFailedCount((int) items.stream().filter(item -> item.getStatus() == PlaceImportJobItemStatus.FAILED).count());
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateJob(job);
    }

    private void failItem(PlaceImportJobItem item, String errorMessage) {
        item.setStatus(PlaceImportJobItemStatus.FAILED);
        // Failed items never enter the moderation list; only successfully
        // resolved mappings can be approved.
        item.setApprovalStatus(PlaceImportApprovalStatus.REJECTED);
        item.setErrorMessage(errorMessage);
        item.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateItem(item);
    }

    private PlaceImportApprovalStatus initialApprovalStatus(PlaceImportJob job) {
        return job.getSourceType() == PlaceImportSourceType.MANUAL
                ? PlaceImportApprovalStatus.APPROVED
                : PlaceImportApprovalStatus.PENDING;
    }

    private void applyManualActivityMapping(PlaceImportJob job, PlaceImportJobItem item, Place place) {
        if (job.getSourceType() != PlaceImportSourceType.MANUAL) {
            return;
        }
        applyApprovedActivityMapping(item, place);
        item.setApprovalStatus(PlaceImportApprovalStatus.APPROVED);
        item.setApprovedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateItem(item);
    }

    private void failBeforeProcessing(UUID jobId, Exception e) {
        log.error("Place import job failed before processing: {}", jobId, e);
        PlaceImportJob job = jobMapper.findJobById(jobId);
        if (job == null) {
            return;
        }
        job.setStatus(PlaceImportJobStatus.FAILED);
        job.setErrorMessage(e.getMessage());
        job.setCompletedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateJob(job);
    }

    private PlaceImportJob requireOwnedJob(UUID userId, UUID jobId) {
        PlaceImportJob job = jobMapper.findJobById(jobId);
        if (job == null || !Objects.equals(job.getUserId(), userId)) {
            throw new IllegalArgumentException("Place import job not found");
        }
        return job;
    }

    private PlaceImportJobResponse toResponse(PlaceImportJob job, List<PlaceImportJobItem> items) {
        return PlaceImportJobResponse.builder()
                .id(job.getId())
                .userId(job.getUserId())
                .sourceType(job.getSourceType())
                .sourceRefId(job.getSourceRefId())
                .status(job.getStatus())
                .maxReviews(job.getMaxReviews())
                .totalItems(job.getTotalItems())
                .skippedExistingCount(job.getSkippedExistingCount())
                .triggeredCount(job.getTriggeredCount())
                .completedCount(job.getCompletedCount())
                .failedCount(job.getFailedCount())
                .errorMessage(job.getErrorMessage())
                .items(items == null ? null : items.stream().map(this::toItemResponse).toList())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private PlaceImportJobItemResponse toItemResponse(PlaceImportJobItem item) {
        return PlaceImportJobItemResponse.builder()
                .id(item.getId())
                .sourceRefId(item.getSourceRefId())
                .activityId(item.getActivityId())
                .sourceUrl(item.getSourceUrl())
                .sourceAddress(item.getSourceAddress())
                .sourceOriginalUrl(item.getSourceOriginalUrl())
                .sourceCandidateKey(item.getSourceCandidateKey())
                .candidateName(item.getCandidateName())
                .googlePlaceId(item.getGooglePlaceId())
                .cid(item.getCid())
                .latitude(item.getLatitude())
                .longitude(item.getLongitude())
                .existingPlaceId(item.getExistingPlaceId())
                .importedPlaceId(item.getImportedPlaceId())
                .pythonJobId(item.getPythonJobId())
                .status(item.getStatus())
                .approvalStatus(item.getApprovalStatus())
                .approvalNote(item.getApprovalNote())
                .approvedAt(item.getApprovedAt())
                .errorMessage(item.getErrorMessage())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private int safeMaxReviews(Integer value) {
        return value == null ? DEFAULT_MAX_REVIEWS : Math.min(Math.max(value, 1), DEFAULT_MAX_REVIEWS);
    }

    private int safeLimit(Integer value, int fallback, int max) {
        return value == null ? fallback : Math.min(Math.max(value, 1), max);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private String candidateKey(Candidate candidate) {
        return PlaceImportCandidateKey.of(
                candidate.googlePlaceId,
                candidate.cid,
                candidate.latitude,
                candidate.longitude,
                candidate.name,
                candidate.url);
    }

    private List<UUID> targetUserIds(UUID requestedUserId, Boolean allUsers) {
        if (Boolean.TRUE.equals(allUsers)) {
            List<UUID> allUserIds = userRepository.findAll().stream()
                    .filter(user -> user.getDeletedAt() == null)
                    .map(user -> user.getId())
                    .toList();
            if (allUserIds.isEmpty()) {
                throw new IllegalArgumentException("No users found");
            }
            return allUserIds;
        }
        if (requestedUserId == null) {
            throw new IllegalArgumentException("User ID is required unless allUsers=true");
        }
        if (userRepository.findById(requestedUserId).isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        return List.of(requestedUserId);
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static class Candidate {
        private UUID sourceRefId;
        private UUID activityId;
        private String sourceCandidateKey;
        private String url;
        private String sourceAddress;
        private String sourceOriginalUrl;
        private String searchQuery;
        private boolean requiresSearch;
        private String name;
        private String googlePlaceId;
        private String cid;
        private BigDecimal latitude;
        private BigDecimal longitude;

        private static Candidate fromMapSearch(UUID socialJobId, JsonNode node) {
            Candidate candidate = new Candidate();
            candidate.sourceRefId = socialJobId;
            candidate.url = text(node, "googleMapsLink", "resolvedUrl");
            candidate.name = text(node, "title", "name");
            candidate.googlePlaceId = text(node, "placeId");
            candidate.cid = text(node, "cid");
            candidate.latitude = decimal(node, "latitude");
            candidate.longitude = decimal(node, "longitude");
            return candidate;
        }

        private static Candidate fromActivity(Activity activity) {
            Candidate candidate = new Candidate();
            candidate.sourceRefId = activity.getTripId();
            candidate.activityId = activity.getId();
            candidate.sourceAddress = activity.getAddress();
            candidate.name = activity.getName();
            // Activity coordinates can be a trip pin, route point, or stale
            // value. Do not use them to target a place scrape.
            candidate.latitude = null;
            candidate.longitude = null;
            candidate.searchQuery = String.join(" ",
                    List.of(activity.getName(), activity.getAddress() == null ? "" : activity.getAddress())).trim();
            candidate.requiresSearch = true;
            return candidate;
        }

        private static Candidate fromManualLink(Activity activity, String url) {
            Candidate candidate = new Candidate();
            candidate.sourceRefId = activity.getTripId();
            candidate.activityId = activity.getId();
            candidate.sourceAddress = activity.getAddress();
            candidate.name = activity.getName();
            candidate.url = url;
            candidate.sourceOriginalUrl = url;
            return candidate;
        }

        private static String text(JsonNode node, String... fields) {
            for (String field : fields) {
                JsonNode value = node.get(field);
                if (value != null && !value.isNull() && !value.asText().isBlank()) {
                    return value.asText();
                }
            }
            return null;
        }

        private static BigDecimal decimal(JsonNode node, String field) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull() || value.asText().isBlank()) {
                return null;
            }
            try {
                return new BigDecimal(value.asText());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
