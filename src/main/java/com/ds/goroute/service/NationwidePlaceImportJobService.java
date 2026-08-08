package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateNationwidePlaceImportJobRequest;
import com.ds.goroute.dto.request.NationwideDuplicateCheckRequest;
import com.ds.goroute.dto.request.NationwideJobEventRequest;
import com.ds.goroute.dto.request.NationwidePlaceImportRequest;
import com.ds.goroute.dto.request.ReviewInput;
import com.ds.goroute.dto.response.NationwidePlaceImportResponse;
import com.ds.goroute.dto.response.NationwideDuplicateCheckResponse;
import com.ds.goroute.dto.response.PlaceImportJobItemResponse;
import com.ds.goroute.dto.response.PlaceImportJobRegionResponse;
import com.ds.goroute.dto.response.PlaceImportJobResponse;
import com.ds.goroute.dto.response.PlaceResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceImportJob;
import com.ds.goroute.entity.PlaceImportJobItem;
import com.ds.goroute.entity.PlaceImportJobRegion;
import com.ds.goroute.mapper.PlaceImportJobMapper;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.thirdparty.scrape.ScrapeJobTriggerResponse;
import com.ds.goroute.thirdparty.scrape.ScrapeNationwideJobRequest;
import com.ds.goroute.thirdparty.scrape.ScrapeServiceClient;
import com.ds.goroute.type.PlaceImportApprovalStatus;
import com.ds.goroute.type.PlaceImportJobItemStatus;
import com.ds.goroute.type.PlaceImportJobStatus;
import com.ds.goroute.type.PlaceImportSourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NationwidePlaceImportJobService {
    private static final String SCORE_SOURCE = "GOOGLE_RECENT_REVIEWS";
    private static final BigDecimal DUPLICATE_DISTANCE_METERS = BigDecimal.valueOf(25);

    private final PlaceImportJobMapper jobMapper;
    private final PlaceRepository placeRepository;
    private final PlaceService placeService;
    private final PlaceReviewService placeReviewService;
    private final PlaceReviewScoreCalculator scoreCalculator;
    private final ScrapeServiceClient scrapeServiceClient;
    private final ObjectMapper objectMapper;

    @Value("${goroute.internal.public-base-url:http://goroute-app:8080}")
    private String publicBaseUrl;

    @Value("${scrape.service.callback-token:}")
    private String callbackToken;

    public PlaceImportJobResponse trigger(CreateNationwidePlaceImportJobRequest request) {
        validateSearchConfiguration(request);
        PlaceImportJob active = jobMapper.findActiveNationwideJob();
        if (active != null) {
            throw new IllegalStateException("A nationwide place import job is already active: " + active.getId());
        }
        LocalDateTime now = LocalDateTime.now();
        PlaceImportJob job = PlaceImportJob.builder()
                .id(UUID.randomUUID())
                .sourceType(PlaceImportSourceType.NATIONWIDE)
                .status(PlaceImportJobStatus.QUEUED)
                .maxReviews(request.getMaxReviews())
                .selectedReviews(request.getSelectedReviews())
                .minReviewCount(request.getMinReviewCount())
                .minAdjustedRating(request.getMinAdjustedRating())
                .totalItems(0)
                .skippedExistingCount(0)
                .triggeredCount(0)
                .completedCount(0)
                .failedCount(0)
                .processedCount(0)
                .eligibleCount(0)
                .importedCount(0)
                .rejectedScoreCount(0)
                .insufficientPhotoCount(0)
                .cancelRequested(false)
                .requestPayload(toJson(request))
                .createdAt(now)
                .updatedAt(now)
                .build();
        jobMapper.insertJob(job);

        String internalBase = publicBaseUrl.replaceAll("/+$", "")
                + "/v1/api/internal/place-import-jobs/nationwide";
        ScrapeJobTriggerResponse trigger = scrapeServiceClient.triggerNationwideJob(
                ScrapeNationwideJobRequest.builder()
                        .gorouteJobId(job.getId().toString())
                        .callbackUrl(internalBase + "/events")
                        .importUrl(internalBase + "/imports")
                        .callbackToken(callbackToken)
                        .maxReviews(request.getMaxReviews())
                        .selectedReviews(request.getSelectedReviews())
                        .lowStarQuota(request.getLowStarQuota())
                        .minReviewCount(request.getMinReviewCount())
                        .minGoogleRating(request.getMinGoogleRating())
                        .minAdjustedRating(request.getMinAdjustedRating())
                        .searchLimitPerQuery(request.getSearchLimitPerQuery())
                        .maxQueriesPerRegion(request.getMaxQueriesPerRegion())
                        .headless(request.getHeadless())
                        .regionCodes(request.getRegionCodes())
                        .queryMode(request.getQueryMode())
                        .customQueries(request.getCustomQueries())
                        .includeRegionalSpecialties(request.getIncludeRegionalSpecialties())
                        .includeTouristAreas(request.getIncludeTouristAreas())
                        .latitude(request.getLatitude())
                        .longitude(request.getLongitude())
                        .radiusKm(request.getRadiusKm())
                        .searchZoom(request.getSearchZoom())
                        .duplicateCheckUrl(internalBase + "/existing-candidates")
                        .build());
        PlaceImportJob persistedJob = jobMapper.findJobById(job.getId());
        if (persistedJob != null) {
            job = persistedJob;
        }
        if (trigger == null || trigger.getJobId() == null || trigger.getJobId().isBlank()) {
            if (job.getStatus() != PlaceImportJobStatus.COMPLETED) {
                job.setStatus(PlaceImportJobStatus.FAILED);
                job.setErrorMessage("Python nationwide job could not be started");
                job.setCompletedAt(LocalDateTime.now());
            }
        } else {
            job.setPythonJobId(trigger.getJobId());
            if (job.getStatus() == PlaceImportJobStatus.QUEUED) {
                job.setStatus(PlaceImportJobStatus.PROCESSING);
                job.setStartedAt(LocalDateTime.now());
            }
        }
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateJob(job);
        return response(job, true);
    }

    @Transactional
    public PlaceImportJobResponse cancel(UUID jobId) {
        PlaceImportJob job = requireNationwideJob(jobId);
        if (job.getStatus() != PlaceImportJobStatus.QUEUED && job.getStatus() != PlaceImportJobStatus.PROCESSING) {
            return response(job, true);
        }
        job.setCancelRequested(true);
        if (job.getPythonJobId() != null) {
            scrapeServiceClient.cancelJob(job.getPythonJobId());
        }
        job.setStatus(PlaceImportJobStatus.CANCELLED);
        job.setCompletedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateJob(job);
        return response(job, true);
    }

    @Transactional
    public void acceptEvent(NationwideJobEventRequest event) {
        PlaceImportJob job = requireNationwideJob(event.getJobId());
        if (job.getPythonJobId() != null && !job.getPythonJobId().equals(event.getPythonJobId())) {
            throw new IllegalArgumentException("Python job id does not match");
        }
        if (job.getPythonJobId() == null) {
            job.setPythonJobId(event.getPythonJobId());
        }
        String type = event.getEventType().trim().toUpperCase(Locale.ROOT);
        if (event.getRegionCode() != null && event.getRegionName() != null) {
            upsertRegion(event);
            job.setCurrentRegionCode(event.getRegionCode());
            job.setCurrentRegionName(event.getRegionName());
        }
        if (!type.startsWith("REGION_")) {
            copyCounters(job, event);
        }
        if ("JOB_COMPLETED".equals(type)) {
            job.setStatus(PlaceImportJobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            job.setCurrentRegionCode(null);
            job.setCurrentRegionName(null);
        } else if ("JOB_FAILED".equals(type)) {
            job.setStatus(PlaceImportJobStatus.FAILED);
            job.setErrorMessage(event.getErrorMessage());
            job.setCompletedAt(LocalDateTime.now());
        } else if ("JOB_CANCELLED".equals(type)) {
            job.setStatus(PlaceImportJobStatus.CANCELLED);
            job.setCompletedAt(LocalDateTime.now());
        } else if (job.getStatus() != PlaceImportJobStatus.CANCELLED) {
            job.setStatus(PlaceImportJobStatus.PROCESSING);
        }
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateJob(job);
    }

    @Transactional
    public NationwidePlaceImportResponse importCandidate(NationwidePlaceImportRequest request) {
        PlaceImportJob job = requireNationwideJob(request.getJobId());
        if (job.getStatus() == PlaceImportJobStatus.CANCELLED || Boolean.TRUE.equals(job.getCancelRequested())) {
            throw new IllegalStateException("Nationwide job has been cancelled");
        }
        String googlePlaceId = request.getPlace().getPlaceId();
        PlaceImportJobItem item = findOrCreateItem(request, googlePlaceId);
        if (item.getStatus() == PlaceImportJobItemStatus.COMPLETED && item.getImportedPlaceId() != null) {
            return NationwidePlaceImportResponse.builder()
                    .imported(true)
                    .outcome(item.getOutcomeReason())
                    .placeId(item.getImportedPlaceId())
                    .googlePlaceId(googlePlaceId)
                    .avgAuthenticityScore(item.getAvgAuthenticityScore())
                    .placeOverallScore(item.getPlaceOverallScore())
                    .adjustedRating(item.getAdjustedRating())
                    .scoredReviewCount(item.getScrapedReviewCount())
                    .selectedReviewCount(item.getSelectedReviewCount())
                    .selectedLowStarCount(0)
                    .build();
        }
        Place existing = findExistingPlace(
                googlePlaceId,
                request.getPlace().getCid(),
                request.getPlace().getLatitude(),
                request.getPlace().getLongitude());
        if (existing != null) {
            item.setExistingPlaceId(existing.getId());
            completeItem(item, PlaceImportJobItemStatus.SKIPPED_EXISTING, "EXISTING_PLACE", null, 0);
            return result(false, "EXISTING_PLACE", existing.getId(), googlePlaceId, null, 0, 0);
        }
        if (request.getPlace().getReviewCount() == null
                || request.getPlace().getReviewCount() < job.getMinReviewCount()) {
            return saveInactive(request, item, googlePlaceId, "REVIEW_COUNT", null);
        }
        BigDecimal minGoogleRating = parseMinGoogleRating(job.getRequestPayload());
        if (request.getPlace().getReviewRating() == null
                || request.getPlace().getReviewRating().compareTo(minGoogleRating) <= 0) {
            return saveInactive(request, item, googlePlaceId, "GOOGLE_RATING", null);
        }
        if (hasText(request.getFilterReason())) {
            return saveInactive(request, item, googlePlaceId,
                    normalizeFilterReason(request.getFilterReason()), null);
        }
        if (request.getReviews().isEmpty()) {
            return saveInactive(request, item, googlePlaceId, "NO_SCORING_REVIEWS", null);
        }

        List<ReviewInput> scoringReviews = request.getReviews().stream()
                .limit(job.getMaxReviews())
                .toList();
        PlaceReviewScoreCalculator.PlaceScoreResult score = scoreCalculator.scoreInputs(
                request.getPlace().getReviewRating(),
                request.getPlace().getReviewCount(),
                scoringReviews);
        if (score == null || score.adjustedRating() == null
                || score.adjustedRating().compareTo(job.getMinAdjustedRating()) <= 0) {
            return saveInactive(request, item, googlePlaceId, "ADJUSTED_RATING", score);
        }

        int lowStarQuota = parseLowStarQuota(job.getRequestPayload());
        List<ReviewInput> selected = scoreCalculator.selectForStorage(
                scoringReviews, job.getSelectedReviews(), lowStarQuota);
        long selectedLowStars = selected.stream()
                .filter(review -> review.getRating() != null && review.getRating() <= 2)
                .count();

        request.getPlace().setUserReviews(null);
        request.getPlace().setVisibilityStatus("ACTIVE");
        PlaceResponse imported = placeService.importPlace(request.getPlace());
        if (imported == null || imported.getId() == null) {
            throw new IllegalStateException("Place import failed for " + googlePlaceId);
        }
        if (!selected.isEmpty()) {
            placeReviewService.batchInsertReviews(selected);
        }
        Place place = placeRepository.findById(imported.getId())
                .orElseThrow(() -> new IllegalStateException("Imported place was not found"));
        place.setAvgAuthenticityScore(score.avgAuthenticityScore());
        place.setPlaceOverallScore(score.placeOverallScore());
        place.setAdjustedRating(score.adjustedRating());
        place.setTrustLevel(score.trustLevel());
        place.setIsJcurveDetected(score.jCurveDetected());
        place.setIsSpikeDetected(score.spikeDetected());
        place.setAuthenticLowStarCount(score.authenticLowStarCount());
        place.setScoreCalculatedAt(LocalDateTime.now());
        place.setScoreSampleCount(score.sampleCount());
        place.setScoreSource(SCORE_SOURCE);
        place.setUpdatedAt(LocalDateTime.now());
        placeRepository.update(place);

        item.setImportedPlaceId(place.getId());
        completeItem(item, PlaceImportJobItemStatus.COMPLETED, "IMPORTED", score, selected.size());
        return result(true, "IMPORTED", place.getId(), googlePlaceId, score,
                selected.size(), Math.toIntExact(selectedLowStars));
    }

    @Transactional(readOnly = true)
    public NationwideDuplicateCheckResponse findExistingCandidates(NationwideDuplicateCheckRequest request) {
        PlaceImportJob job = requireNationwideJob(request.getJobId());
        if (job.getPythonJobId() != null && !job.getPythonJobId().equals(request.getPythonJobId())) {
            throw new IllegalArgumentException("Python job id does not match");
        }

        List<String> placeIds = request.getCandidates().stream()
                .map(NationwideDuplicateCheckRequest.Candidate::getGooglePlaceId)
                .filter(this::hasText)
                .distinct()
                .toList();
        List<String> cids = request.getCandidates().stream()
                .map(NationwideDuplicateCheckRequest.Candidate::getCid)
                .filter(this::hasText)
                .distinct()
                .toList();
        Set<String> existingPlaceIds = placeIds.isEmpty()
                ? Set.of() : Set.copyOf(placeRepository.findExistingPlaceIds(placeIds));
        Set<String> existingCids = cids.isEmpty()
                ? Set.of() : Set.copyOf(placeRepository.findExistingCids(cids));
        Set<String> existingKeys = request.getCandidates().stream()
                .filter(candidate -> existingPlaceIds.contains(candidate.getGooglePlaceId())
                        || existingCids.contains(candidate.getCid())
                        || (!hasText(candidate.getGooglePlaceId()) && !hasText(candidate.getCid())
                        && candidate.getLatitude() != null && candidate.getLongitude() != null
                        && placeRepository.findNearCoordinates(candidate.getLatitude(), candidate.getLongitude(),
                        DUPLICATE_DISTANCE_METERS) != null))
                .map(NationwideDuplicateCheckRequest.Candidate::getCandidateKey)
                .collect(Collectors.toSet());
        return NationwideDuplicateCheckResponse.builder().existingCandidateKeys(existingKeys).build();
    }

    private Place findExistingPlace(String googlePlaceId, String cid, BigDecimal latitude, BigDecimal longitude) {
        if (hasText(googlePlaceId)) {
            Place place = placeRepository.findByPlaceId(googlePlaceId);
            if (place != null) {
                return place;
            }
        }
        if (hasText(cid)) {
            Place place = placeRepository.findByCid(cid);
            if (place != null) {
                return place;
            }
        }
        if (latitude != null && longitude != null) {
            return placeRepository.findNearCoordinates(latitude, longitude, DUPLICATE_DISTANCE_METERS);
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public PlaceImportJobResponse get(UUID jobId) {
        return response(requireNationwideJob(jobId), true);
    }

    public PlaceImportJobResponse retry(UUID jobId) {
        PlaceImportJob oldJob = requireNationwideJob(jobId);
        if (oldJob.getStatus() != PlaceImportJobStatus.FAILED
                && oldJob.getStatus() != PlaceImportJobStatus.CANCELLED) {
            throw new IllegalStateException("Only failed or cancelled nationwide jobs can be retried");
        }
        CreateNationwidePlaceImportJobRequest request;
        try {
            request = objectMapper.readValue(oldJob.getRequestPayload(), CreateNationwidePlaceImportJobRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Stored nationwide job configuration is invalid", e);
        }
        return trigger(request);
    }

    private PlaceImportJobItem findOrCreateItem(NationwidePlaceImportRequest request, String googlePlaceId) {
        PlaceImportJobItem item = jobMapper.findItemByJobAndGooglePlaceId(request.getJobId(), googlePlaceId);
        LocalDateTime now = LocalDateTime.now();
        if (item == null) {
            item = PlaceImportJobItem.builder()
                    .id(UUID.randomUUID())
                    .jobId(request.getJobId())
                    .sourceUrl(request.getPlace().getGoogleMapsLink())
                    .sourceAddress(request.getPlace().getAddress())
                    .sourceCandidateKey(googlePlaceId)
                    .candidateName(request.getPlace().getTitle())
                    .googlePlaceId(googlePlaceId)
                    .cid(request.getPlace().getCid())
                    .latitude(request.getPlace().getLatitude())
                    .longitude(request.getPlace().getLongitude())
                    .pythonJobId(request.getPythonJobId())
                    .regionCode(request.getRegionCode())
                    .regionName(request.getRegionName())
                    .searchQuery(request.getSearchQuery())
                    .reviewCount(request.getPlace().getReviewCount())
                    .scrapedReviewCount(request.getReviews().size())
                    .status(PlaceImportJobItemStatus.PROCESSING)
                    .approvalStatus(PlaceImportApprovalStatus.APPROVED)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            jobMapper.insertItem(item);
        }
        return item;
    }

    private void completeItem(PlaceImportJobItem item, PlaceImportJobItemStatus status, String outcome,
                              PlaceReviewScoreCalculator.PlaceScoreResult score, int selectedCount) {
        item.setStatus(status);
        item.setOutcomeReason(outcome);
        item.setSelectedReviewCount(selectedCount);
        if (score != null) {
            item.setAvgAuthenticityScore(score.avgAuthenticityScore());
            item.setPlaceOverallScore(score.placeOverallScore());
            item.setAdjustedRating(score.adjustedRating());
        }
        item.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateItem(item);
    }

    private void upsertRegion(NationwideJobEventRequest event) {
        LocalDateTime now = LocalDateTime.now();
        String status = event.getRegionStatus() == null ? "PROCESSING" : event.getRegionStatus();
        jobMapper.upsertRegion(PlaceImportJobRegion.builder()
                .id(UUID.randomUUID())
                .jobId(event.getJobId())
                .regionCode(event.getRegionCode())
                .regionName(event.getRegionName())
                .priority(value(event.getPriority()))
                .sequenceNo(value(event.getSequenceNo()))
                .status(status)
                .queryCount(value(event.getQueryCount()))
                .discoveredCount(value(event.getDiscoveredCount()))
                .processedCount(value(event.getProcessedCount()))
                .eligibleCount(value(event.getEligibleCount()))
                .importedCount(value(event.getImportedCount()))
                .skippedCount(value(event.getSkippedCount()))
                .failedCount(value(event.getFailedCount()))
                .errorMessage(event.getErrorMessage())
                .startedAt("PROCESSING".equalsIgnoreCase(status) ? now : null)
                .completedAt(isTerminalRegion(status) ? now : null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private void copyCounters(PlaceImportJob job, NationwideJobEventRequest event) {
        if (event.getDiscoveredCount() != null) job.setTotalItems(event.getDiscoveredCount());
        if (event.getProcessedCount() != null) job.setProcessedCount(event.getProcessedCount());
        if (event.getEligibleCount() != null) job.setEligibleCount(event.getEligibleCount());
        if (event.getImportedCount() != null) {
            job.setImportedCount(event.getImportedCount());
            job.setCompletedCount(event.getImportedCount());
        }
        if (event.getSkippedCount() != null) job.setSkippedExistingCount(event.getSkippedCount());
        if (event.getFailedCount() != null) job.setFailedCount(event.getFailedCount());
        if (event.getRejectedScoreCount() != null) job.setRejectedScoreCount(event.getRejectedScoreCount());
        if (event.getInsufficientPhotoCount() != null) job.setInsufficientPhotoCount(event.getInsufficientPhotoCount());
    }

    private PlaceImportJobResponse response(PlaceImportJob job, boolean details) {
        CreateNationwidePlaceImportJobRequest config = parseConfiguration(job.getRequestPayload());
        List<PlaceImportJobItemResponse> items = details
                ? jobMapper.findItemsByJobId(job.getId()).stream().map(this::itemResponse).toList()
                : null;
        List<PlaceImportJobRegionResponse> regions = details
                ? jobMapper.findRegionsByJobId(job.getId()).stream().map(this::regionResponse).toList()
                : null;
        return PlaceImportJobResponse.builder()
                .id(job.getId()).userId(job.getUserId()).sourceType(job.getSourceType())
                .status(job.getStatus()).maxReviews(job.getMaxReviews()).selectedReviews(job.getSelectedReviews())
                .minReviewCount(job.getMinReviewCount()).minAdjustedRating(job.getMinAdjustedRating())
                .minGoogleRating(config == null ? null : config.getMinGoogleRating())
                .queryMode(config == null ? null : config.getQueryMode())
                .customQueries(config == null ? null : config.getCustomQueries())
                .regionCodes(config == null ? null : config.getRegionCodes())
                .includeRegionalSpecialties(config == null ? null : config.getIncludeRegionalSpecialties())
                .includeTouristAreas(config == null ? null : config.getIncludeTouristAreas())
                .latitude(config == null ? null : config.getLatitude())
                .longitude(config == null ? null : config.getLongitude())
                .radiusKm(config == null ? null : config.getRadiusKm())
                .searchZoom(config == null ? null : config.getSearchZoom())
                .totalItems(job.getTotalItems()).processedCount(job.getProcessedCount())
                .eligibleCount(job.getEligibleCount()).importedCount(job.getImportedCount())
                .completedCount(job.getCompletedCount()).failedCount(job.getFailedCount())
                .skippedExistingCount(job.getSkippedExistingCount()).rejectedScoreCount(job.getRejectedScoreCount())
                .insufficientPhotoCount(job.getInsufficientPhotoCount()).cancelRequested(job.getCancelRequested())
                .pythonJobId(job.getPythonJobId()).currentRegionCode(job.getCurrentRegionCode())
                .currentRegionName(job.getCurrentRegionName()).errorMessage(job.getErrorMessage())
                .items(items).regions(regions).createdAt(job.getCreatedAt()).startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt()).updatedAt(job.getUpdatedAt()).build();
    }

    private void validateSearchConfiguration(CreateNationwidePlaceImportJobRequest request) {
        if ((request.getLatitude() == null) != (request.getLongitude() == null)) {
            throw new IllegalArgumentException("latitude and longitude must be provided together");
        }
        if ("REPLACE".equalsIgnoreCase(request.getQueryMode())
                && (request.getCustomQueries() == null
                || request.getCustomQueries().stream().noneMatch(this::hasText))) {
            throw new IllegalArgumentException("customQueries is required when queryMode is REPLACE");
        }
    }

    private CreateNationwidePlaceImportJobRequest parseConfiguration(String payload) {
        if (!hasText(payload)) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, CreateNationwidePlaceImportJobRequest.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private PlaceImportJobItemResponse itemResponse(PlaceImportJobItem item) {
        return PlaceImportJobItemResponse.builder()
                .id(item.getId()).sourceUrl(item.getSourceUrl()).sourceAddress(item.getSourceAddress())
                .candidateName(item.getCandidateName()).googlePlaceId(item.getGooglePlaceId()).cid(item.getCid())
                .latitude(item.getLatitude()).longitude(item.getLongitude()).existingPlaceId(item.getExistingPlaceId())
                .importedPlaceId(item.getImportedPlaceId()).pythonJobId(item.getPythonJobId())
                .regionCode(item.getRegionCode()).regionName(item.getRegionName()).searchQuery(item.getSearchQuery())
                .reviewCount(item.getReviewCount()).scrapedReviewCount(item.getScrapedReviewCount())
                .selectedReviewCount(item.getSelectedReviewCount()).avgAuthenticityScore(item.getAvgAuthenticityScore())
                .placeOverallScore(item.getPlaceOverallScore()).adjustedRating(item.getAdjustedRating())
                .outcomeReason(item.getOutcomeReason()).status(item.getStatus()).errorMessage(item.getErrorMessage())
                .createdAt(item.getCreatedAt()).updatedAt(item.getUpdatedAt()).build();
    }

    private PlaceImportJobRegionResponse regionResponse(PlaceImportJobRegion region) {
        return PlaceImportJobRegionResponse.builder()
                .regionCode(region.getRegionCode()).regionName(region.getRegionName()).priority(region.getPriority())
                .sequenceNo(region.getSequenceNo()).status(region.getStatus()).queryCount(region.getQueryCount())
                .discoveredCount(region.getDiscoveredCount()).processedCount(region.getProcessedCount())
                .eligibleCount(region.getEligibleCount()).importedCount(region.getImportedCount())
                .skippedCount(region.getSkippedCount()).failedCount(region.getFailedCount())
                .errorMessage(region.getErrorMessage()).startedAt(region.getStartedAt())
                .completedAt(region.getCompletedAt()).updatedAt(region.getUpdatedAt()).build();
    }

    private NationwidePlaceImportResponse result(boolean imported, String outcome, UUID placeId,
                                                 String googlePlaceId,
                                                 PlaceReviewScoreCalculator.PlaceScoreResult score,
                                                 int selected, int selectedLowStars) {
        return NationwidePlaceImportResponse.builder()
                .imported(imported).outcome(outcome).placeId(placeId).googlePlaceId(googlePlaceId)
                .avgAuthenticityScore(score == null ? null : score.avgAuthenticityScore())
                .placeOverallScore(score == null ? null : score.placeOverallScore())
                .adjustedRating(score == null ? null : score.adjustedRating())
                .scoredReviewCount(score == null ? 0 : score.sampleCount())
                .selectedReviewCount(selected).selectedLowStarCount(selectedLowStars).build();
    }

    private PlaceImportJob requireNationwideJob(UUID jobId) {
        PlaceImportJob job = jobMapper.findJobById(jobId);
        if (job == null || job.getSourceType() != PlaceImportSourceType.NATIONWIDE) {
            throw new IllegalArgumentException("Nationwide place import job not found");
        }
        return job;
    }

    private int parseLowStarQuota(String payload) {
        try {
            return objectMapper.readTree(payload).path("lowStarQuota").asInt(4);
        } catch (Exception ignored) {
            return 4;
        }
    }

    private BigDecimal parseMinGoogleRating(String payload) {
        CreateNationwidePlaceImportJobRequest config = parseConfiguration(payload);
        return config == null || config.getMinGoogleRating() == null
                ? BigDecimal.valueOf(4.00)
                : config.getMinGoogleRating();
    }

    private NationwidePlaceImportResponse saveInactive(NationwidePlaceImportRequest request,
                                                        PlaceImportJobItem item,
                                                        String googlePlaceId,
                                                        String reason,
                                                        PlaceReviewScoreCalculator.PlaceScoreResult score) {
        request.getPlace().setUserReviews(null);
        request.getPlace().setVisibilityStatus("INACTIVE");
        request.getPlace().setImages(firstImageOnly(request.getPlace().getImages()));
        PlaceResponse imported = placeService.importPlace(request.getPlace());
        if (imported == null || imported.getId() == null) {
            throw new IllegalStateException("Inactive place import failed for " + googlePlaceId);
        }
        Place place = placeRepository.findById(imported.getId())
                .orElseThrow(() -> new IllegalStateException("Imported inactive place was not found"));
        if (score != null) {
            place.setAvgAuthenticityScore(score.avgAuthenticityScore());
            place.setPlaceOverallScore(score.placeOverallScore());
            place.setAdjustedRating(score.adjustedRating());
            place.setTrustLevel(score.trustLevel());
            place.setIsJcurveDetected(score.jCurveDetected());
            place.setIsSpikeDetected(score.spikeDetected());
            place.setAuthenticLowStarCount(score.authenticLowStarCount());
            place.setScoreCalculatedAt(LocalDateTime.now());
            place.setScoreSampleCount(score.sampleCount());
            place.setScoreSource(SCORE_SOURCE);
            place.setUpdatedAt(LocalDateTime.now());
            placeRepository.update(place);
        }
        String outcome = "SAVED_INACTIVE_" + normalizeFilterReason(reason);
        item.setImportedPlaceId(place.getId());
        completeItem(item, PlaceImportJobItemStatus.COMPLETED, outcome, score, 0);
        return result(true, outcome, place.getId(), googlePlaceId, score, 0, 0);
    }

    private String firstImageOnly(String images) {
        if (!hasText(images)) {
            return "[]";
        }
        try {
            var parsed = objectMapper.readTree(images);
            if (!parsed.isArray() || parsed.isEmpty()) {
                return "[]";
            }
            return objectMapper.createArrayNode().add(parsed.get(0)).toString();
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private String normalizeFilterReason(String reason) {
        String normalized = reason == null ? "FILTERED" : reason.trim().toUpperCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^A-Z0-9_]+", "_");
        return normalized.isBlank() ? "FILTERED" : normalized;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private static boolean isTerminalRegion(String status) {
        return List.of("COMPLETED", "FAILED", "CANCELLED").contains(status.toUpperCase(Locale.ROOT));
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }
}
