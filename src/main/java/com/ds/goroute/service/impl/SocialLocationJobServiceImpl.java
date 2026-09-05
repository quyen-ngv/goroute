package com.ds.goroute.service.impl;

import com.ds.goroute.config.InternalApiProperties;
import com.ds.goroute.dto.request.CreateSocialLocationJobRequest;
import com.ds.goroute.dto.request.SocialLocationJobCallbackRequest;
import com.ds.goroute.dto.request.CreateSocialPlaceImportJobRequest;
import com.ds.goroute.dto.response.SocialLocationJobResponse;
import com.ds.goroute.entity.SocialLocationJob;
import com.ds.goroute.entity.AiApiCall;
import com.ds.goroute.entity.PlaceImportJobItem;
import com.ds.goroute.mapper.AiApiCallMapper;
import com.ds.goroute.mapper.PlaceImportJobMapper;
import com.ds.goroute.mapper.PlaceMapper;
import com.ds.goroute.mapper.SocialLocationJobMapper;
import com.ds.goroute.entity.Place;
import com.ds.goroute.service.SocialLocationJobService;
import com.ds.goroute.service.PlaceImportJobService;
import com.ds.goroute.service.PlaceSocialVideoService;
import com.ds.goroute.service.SocialLocationConfigService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.SocialLocationCompletionService;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.repository.SocialLocationRestrictionRepository;
import com.ds.goroute.entity.SocialLocationSubmissionEvent;
import com.ds.goroute.entity.SocialLocationUserRestriction;
import com.ds.goroute.type.SocialLocationRestrictionStatus;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.thirdparty.scrape.ScrapeServiceClient;
import com.ds.goroute.thirdparty.scrape.ScrapeSocialLocationJobRequest;
import com.ds.goroute.thirdparty.scrape.ScrapeSocialLocationJobResponse;
import com.ds.goroute.type.SocialLocationJobStatus;
import com.ds.goroute.type.PlaceImportJobItemStatus;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.SocialLocationOperation;
import com.ds.goroute.utils.SocialLocationSourceKey;
import com.ds.goroute.utils.PlaceImportCandidateKey;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.UUID;
import java.time.LocalDate;
import java.net.URI;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialLocationJobServiceImpl implements SocialLocationJobService {

    private static final String CURRENT_CANDIDATE_POLICY = "AI_EVIDENCE_JUDGE_V2";

    private final SocialLocationJobMapper jobMapper;
    private final AiApiCallMapper aiApiCallMapper;
    private final PlaceImportJobMapper placeImportJobMapper;
    private final PlaceMapper placeMapper;
    private final ScrapeServiceClient scrapeServiceClient;
    private final ObjectMapper objectMapper;
    private final PlaceImportJobService placeImportJobService;
    private final PlaceSocialVideoService placeSocialVideoService;
    private final SocialLocationConfigService socialConfig;
    private final AiTripRepository aiTripRepository;
    private final SocialLocationRestrictionRepository restrictionRepository;
    private final NotificationService notificationService;
    private final SocialLocationCompletionService completionService;

    @Value("${goroute.internal.public-base-url:http://goroute-app:8080}")
    private String internalBaseUrl;

    private final InternalApiProperties internalApiProperties;

    @Value("${social-location.dispatch-timeout-seconds:90}")
    private long dispatchTimeoutSeconds;

    @Value("${social-location.job-timeout-minutes:15}")
    private long jobTimeoutMinutes;

    @Value("${social-location.reconcile-interval-seconds:20}")
    private long reconcileIntervalSeconds;

    @Value("${social-location.max-attempts:2}")
    private int maxAttempts;

    @Override
    @Transactional
    public SocialLocationJobResponse create(UUID userId, CreateSocialLocationJobRequest request) {
        String sourceUrl = request.getUrl().trim();
        jobMapper.lockUserSubmission(userId);
        jobMapper.lockSubmissionQueue();
        enforceRestriction(userId, sourceUrl);
        String platform = platformFromUrl(sourceUrl);
        if ("unknown".equals(platform)) {
            audit(userId, null, sourceUrl, "REJECTED_URL", "UNSUPPORTED_SOCIAL_URL", null);
            throw new IllegalArgumentException("URL must be a TikTok or Instagram URL");
        }
        String sourceKey = SocialLocationSourceKey.fromUrl(sourceUrl);
        SocialLocationOperation requestedOperation = requestedOperation(request);
        SocialLocationJob reusableJob = jobMapper.findReusableByUserIdAndSourceKey(userId, sourceKey);
        SocialLocationJob stalePolicyJob = null;
        if (reusableJob != null
                && reusableJob.getStatus() == SocialLocationJobStatus.COMPLETED
                && !usesCurrentCandidatePolicy(reusableJob)) {
            stalePolicyJob = reusableJob;
            reusableJob = null;
        }
        if (reusableJob != null) {
            if (reusableJob.getOperation() != requestedOperation) {
                // The extraction is shared, but the requested projection is not. Persist the
                // latest button choice while the worker is still running so completion cannot
                // accidentally start an itinerary after the user selected "save spots" (or
                // vice versa).
                reusableJob.setOperation(requestedOperation);
                jobMapper.update(reusableJob);
            }
            if (reusableJob.getStatus() == SocialLocationJobStatus.COMPLETED
                    && reusableJob.getResultPayload() != null) {
                // The extraction is the cache. A later button press may project the same cached
                // result into either saved spots or an itinerary without downloading/analyzing
                // the video again.
                JsonNode cachedResult = retainOnlyCertainCandidates(
                        enrichResultWithPlaceMappings(reusableJob.getId(), parseJson(reusableJob.getResultPayload())));
                try {
                    completionService.handleCompleted(reusableJob, cachedResult);
                } catch (Exception e) {
                    // A cache hit must remain usable even if a downstream projection is
                    // temporarily unavailable. The next request can retry the projection
                    // without downloading or extracting the source video again.
                    log.warn("Could not project cached social job {}: {}",
                            reusableJob.getId(), e.getMessage(), e);
                }
            }
            return toResponse(reusableJob);
        }

        if (requestedOperation == SocialLocationOperation.SAVE_SPOTS) {
            enforceDailyLimit(userId, sourceUrl);
        }
        if (jobMapper.countQueued() >= socialConfig.maxQueuedJobs()) {
            audit(userId, null, sourceUrl, "REJECTED_QUEUE_FULL", "QUEUE_FULL", null);
            throw new BusinessException(ErrorConstant.SOCIAL_LOCATION_QUEUE_FULL,
                    Map.of("maxQueuedJobs", socialConfig.maxQueuedJobs()));
        }

        String userTier = aiTripRepository.getSubscriptionTier(userId);
        int maxDurationSeconds = socialConfig.maxVideoSeconds(userTier);

        LocalDateTime now = LocalDateTime.now();
        SocialLocationJob job = SocialLocationJob.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .sourceUrl(sourceUrl)
                .sourceKey(sourceKey)
                .platform(platform)
                .operation(requestedOperation)
                .status(SocialLocationJobStatus.QUEUED)
                .language(cleanLanguage(request.getLanguage()))
                .userTier(userTier)
                .maxDurationSeconds(maxDurationSeconds)
                .attemptCount(0)
                .requestPayload(toJson(request))
                .createdAt(now)
                .updatedAt(now)
                .build();
        if (stalePolicyJob != null) {
            if (jobMapper.markDeletedByIdAndUserId(stalePolicyJob.getId(), userId) == 0) {
                throw new IllegalStateException("Could not invalidate stale social-location result");
            }
            audit(userId, stalePolicyJob.getId(), sourceUrl, "STALE_RESULT_INVALIDATED",
                    "CANDIDATE_POLICY_CHANGED", Map.of("requiredPolicy", CURRENT_CANDIDATE_POLICY));
        }
        try {
            jobMapper.insert(job);
        } catch (DataIntegrityViolationException duplicate) {
            SocialLocationJob existing = jobMapper.findReusableByUserIdAndSourceKey(userId, sourceKey);
            if (existing != null) {
                return toResponse(existing);
            }
            throw duplicate;
        }
        audit(userId, job.getId(), sourceUrl, "SUBMITTED", null,
                Map.of("tier", userTier,
                        "maxDurationSeconds", maxDurationSeconds));
        return toResponse(job);
    }

    private void enforceDailyLimit(UUID userId, String sourceUrl) {
        int dailyJobLimit = socialConfig.dailyJobLimit(userId);
        if (jobMapper.countCreatedByUserSince(userId, LocalDate.now().atStartOfDay()) < dailyJobLimit) {
            return;
        }
        audit(userId, null, sourceUrl, "REJECTED_DAILY_LIMIT", "DAILY_LIMIT_REACHED", null);
        throw new BusinessException(ErrorConstant.SOCIAL_LOCATION_DAILY_LIMIT_REACHED,
                Map.of("dailyLimit", dailyJobLimit));
    }

    @Override
    @Transactional
    public void dispatchQueuedJobs() {
        if (!jobMapper.tryDispatchLock()) return;
        recoverStaleDispatches();
        reconcileProcessingJobs();
        int available = socialConfig.maxConcurrentJobs() - jobMapper.countActive();
        if (available <= 0) return;
        for (SocialLocationJob job : jobMapper.claimQueued(available)) {
            dispatch(job);
        }
    }

    private void dispatch(SocialLocationJob job) {
        int interval = socialConfig.frameIntervalSeconds();
        int maxFrames = Math.max(1, (int) Math.ceil(job.getMaxDurationSeconds() / (double) interval));
        ScrapeSocialLocationJobResponse trigger = scrapeServiceClient.triggerSocialLocationJob(
                ScrapeSocialLocationJobRequest.builder()
                        .url(job.getSourceUrl())
                        .language(job.getLanguage())
                        .callbackUrl(callbackUrl())
                        .callbackToken(internalApiProperties.scrapeCallbackToken())
                        .gorouteJobId(job.getId())
                        .maxDurationSeconds(job.getMaxDurationSeconds())
                        .maxAudioSeconds(job.getMaxDurationSeconds())
                        .maxFrames(Math.min(maxFrames, 100))
                        .frameIntervalSeconds(interval)
                        .imageMaxWidth(socialConfig.imageMaxWidth())
                        .imageJpegQuality(socialConfig.imageJpegQuality())
                        .aiProvider(socialConfig.aiProvider())
                        .aiModel(socialConfig.aiModel())
                        .aiBaseUrl(socialConfig.aiBaseUrl())
                        .includeMapSearch(true)
                        .mapSearchLimit(1)
                        .headless(true)
                        .build());

        if (trigger == null || trigger.getJobId() == null || trigger.getJobId().isBlank()) {
            retryOrFinish(job, "DISPATCH", "PYTHON_TRIGGER_FAILED",
                    "Could not submit the job to the social-location worker");
        } else {
            SocialLocationJob latest = jobMapper.findById(job.getId());
            if (latest != null && isTerminal(latest.getStatus())) return;
            job.setStatus(SocialLocationJobStatus.PROCESSING);
            job.setPythonJobId(trigger.getJobId());
            LocalDateTime now = LocalDateTime.now();
            job.setStartedAt(now);
            job.setDeadlineAt(now.plusMinutes(Math.max(1, jobTimeoutMinutes)));
            job.setLastHeartbeatAt(now);
            job.setLastReconciledAt(now);
            job.setErrorCode(null);
            job.setErrorMessage(null);
            job.setErrorDetails(null);
            job.setFailureStage(null);
        }
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.update(job);
        if (job.getStatus() == SocialLocationJobStatus.QUEUED || isTerminal(job.getStatus())) {
            auditLifecycleRecovery(job);
        }
    }

    private void recoverStaleDispatches() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(Math.max(15, dispatchTimeoutSeconds));
        for (SocialLocationJob job : jobMapper.findStaleDispatching(cutoff, 25)) {
            retryOrFinish(job, "DISPATCH", "DISPATCH_TIMEOUT",
                    "The social-location worker did not acknowledge the job in time");
            if (jobMapper.updateIfStatus(job, "DISPATCHING") == 0) continue;
            auditLifecycleRecovery(job);
            log.warn("Recovered stale social-location dispatch: job_id={} attempt={} status={}",
                    job.getId(), job.getAttemptCount(), job.getStatus());
        }
    }

    private void reconcileProcessingJobs() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reconcileBefore = now.minusSeconds(Math.max(5, reconcileIntervalSeconds));
        for (SocialLocationJob candidate : jobMapper.findProcessingForReconciliation(reconcileBefore, 25)) {
            Map<String, Object> workerJob = candidate.getPythonJobId() == null
                    ? null
                    : scrapeServiceClient.pollJobData(candidate.getPythonJobId());
            if (applyWorkerTerminalResult(candidate, workerJob)) {
                continue;
            }

            SocialLocationJob latest = jobMapper.findById(candidate.getId());
            if (latest == null || latest.getStatus() != SocialLocationJobStatus.PROCESSING) {
                continue;
            }
            latest.setLastReconciledAt(now);
            if (workerJob != null) {
                latest.setLastHeartbeatAt(now);
            }
            if (latest.getDeadlineAt() != null && !latest.getDeadlineAt().isAfter(now)) {
                retryOrFinish(latest, "PROCESSING", "WORKER_TIMEOUT",
                        workerJob == null
                                ? "The social-location worker no longer reports this job"
                                : "The social-location job exceeded its processing deadline");
            } else {
                latest.setUpdatedAt(now);
            }
            if (jobMapper.updateIfStatus(latest, "PROCESSING") > 0
                    && (latest.getStatus() == SocialLocationJobStatus.QUEUED
                    || isTerminal(latest.getStatus()))) {
                auditLifecycleRecovery(latest);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean applyWorkerTerminalResult(SocialLocationJob job, Map<String, Object> workerJob) {
        if (workerJob == null) return false;
        String workerStatus = String.valueOf(workerJob.getOrDefault("status", "")).trim().toLowerCase(Locale.ROOT);
        if (!List.of("completed", "failed", "cancelled").contains(workerStatus)) return false;

        Map<String, Object> wrapper = workerJob.get("result") instanceof Map<?, ?> value
                ? (Map<String, Object>) value : Map.of();
        Object extractionValue = wrapper.get("extraction");
        JsonNode extraction = extractionValue == null ? null : objectMapper.valueToTree(extractionValue);
        if (extraction == null && wrapper.containsKey("success")) {
            extraction = objectMapper.valueToTree(wrapper);
        }

        String status = "FAILED";
        JsonNode error = workerJob.get("error") == null ? null : objectMapper.valueToTree(workerJob.get("error"));
        if (extraction != null && extraction.isObject()) {
            String rejectedStatus = extraction.path("rejectedStatus").asText("");
            if (!rejectedStatus.isBlank()) {
                status = rejectedStatus;
            } else if (extraction.path("success").asBoolean(false)) {
                status = "COMPLETED";
            }
            if (error == null && extraction.hasNonNull("error")) {
                error = normalizeError(extraction.get("error"), "EXTRACTION_FAILED");
            }
        }
        if ("cancelled".equals(workerStatus)) {
            error = errorNode("WORKER_CANCELLED", "The social-location worker cancelled the job");
        } else if (error == null && "FAILED".equals(status)) {
            error = errorNode("WORKER_FAILED", "The social-location worker failed without an error payload");
        }

        handleCallback(SocialLocationJobCallbackRequest.builder()
                .gorouteJobId(job.getId())
                .pythonJobId(job.getPythonJobId())
                .status(status)
                .result(extraction)
                .error(error)
                .build());
        log.info("Reconciled social-location job from worker poll: job_id={} worker_status={} status={}",
                job.getId(), workerStatus, status);
        return true;
    }

    private void retryOrFinish(SocialLocationJob job, String stage, String code, String message) {
        LocalDateTime now = LocalDateTime.now();
        int attempt = job.getAttemptCount() == null ? 0 : job.getAttemptCount();
        job.setFailureStage(stage);
        job.setErrorCode(code);
        job.setErrorMessage(message);
        job.setErrorDetails(toJson(Map.of("stage", stage, "attempt", attempt, "maxAttempts", maxAttempts)));
        job.setPythonJobId(null);
        job.setDeadlineAt(null);
        job.setLastHeartbeatAt(null);
        job.setLastReconciledAt(now);
        if (attempt < Math.max(1, maxAttempts)) {
            job.setStatus(SocialLocationJobStatus.QUEUED);
            job.setNextAttemptAt(now.plusSeconds(Math.max(5, 15L * Math.max(1, attempt))));
            job.setStartedAt(null);
            job.setCompletedAt(null);
        } else if ("PROCESSING".equals(stage)) {
            job.setStatus(SocialLocationJobStatus.TIMED_OUT);
            job.setNextAttemptAt(null);
            job.setCompletedAt(now);
        } else {
            job.setStatus(SocialLocationJobStatus.FAILED);
            job.setNextAttemptAt(null);
            job.setCompletedAt(now);
        }
        job.setUpdatedAt(now);
    }

    private void auditLifecycleRecovery(SocialLocationJob job) {
        audit(job.getUserId(), job.getId(), job.getSourceUrl(),
                job.getStatus() == SocialLocationJobStatus.QUEUED ? "JOB_RETRY_SCHEDULED" : "JOB_TERMINATED",
                job.getErrorCode(), Map.of(
                        "stage", job.getFailureStage() == null ? "UNKNOWN" : job.getFailureStage(),
                        "attempt", job.getAttemptCount() == null ? 0 : job.getAttemptCount(),
                        "status", job.getStatus().name()));
    }

    @Override
    public SocialLocationJobResponse get(UUID userId, UUID jobId) {
        SocialLocationJob job = jobMapper.findById(jobId);
        if (job == null || !job.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Social location job not found");
        }
        return toResponse(job);
    }

    @Override
    public List<SocialLocationJobResponse> listMine(UUID userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return jobMapper.findByUserId(userId, safeSize, safePage * safeSize)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID jobId) {
        jobMapper.lockUserSubmission(userId);
        SocialLocationJob job = jobMapper.findById(jobId);
        if (job == null || !job.getUserId().equals(userId)
                || job.getStatus() == SocialLocationJobStatus.DELETED) {
            throw new IllegalArgumentException("Social location job not found");
        }
        if (jobMapper.markDeletedByIdAndUserId(jobId, userId) == 0) {
            throw new IllegalArgumentException("Social location job not found");
        }
        audit(userId, jobId, job.getSourceUrl(), "DELETED", null, null);
    }

    @Override
    @Transactional
    public SocialLocationJobResponse handleCallback(SocialLocationJobCallbackRequest request) {
        SocialLocationJob job = request.getGorouteJobId() != null
                ? jobMapper.findById(request.getGorouteJobId())
                : null;
        if (job == null && request.getPythonJobId() != null) {
            job = jobMapper.findByPythonJobId(request.getPythonJobId());
        }
        if (job == null) {
            throw new IllegalArgumentException("Social location job not found");
        }
        jobMapper.lockUserSubmission(job.getUserId());
        job = jobMapper.findById(job.getId());

        SocialLocationJobStatus status = parseStatus(request.getStatus());
        if (isTerminal(job.getStatus())) {
            return toResponse(job);
        }
        job.setStatus(status);
        job.setPythonJobId(firstNonBlank(request.getPythonJobId(), job.getPythonJobId()));
        job.setSourceUrl(firstNonBlank(request.getSourceUrl(), job.getSourceUrl()));
        job.setPlatform(firstNonBlank(request.getPlatform(), job.getPlatform()));
        JsonNode callbackResult = retainOnlyCertainCandidates(
                attachExistingDatabasePlaces(request.getResult()));
        request.setResult(callbackResult);
        job.setResultPayload(callbackResult != null && !callbackResult.isNull()
                ? callbackResult.toString()
                : job.getResultPayload());
        JsonNode duration = callbackResult == null ? null : callbackResult.path("metadata").get("duration");
        if (duration != null && duration.canConvertToInt()) {
            job.setVideoDurationSeconds(duration.asInt());
        }
        applyError(job, request.getError());
        job.setLastHeartbeatAt(LocalDateTime.now());
        job.setLastReconciledAt(LocalDateTime.now());
        job.setFailureStage(status == SocialLocationJobStatus.FAILED ? "PROCESSING" : null);
        job.setErrorDetails(request.getError() == null || request.getError().isNull()
                ? null : request.getError().toString());
        if (status == SocialLocationJobStatus.COMPLETED
                || status == SocialLocationJobStatus.FAILED
                || status == SocialLocationJobStatus.TIMED_OUT
                || status == SocialLocationJobStatus.REJECTED_DURATION
                || status == SocialLocationJobStatus.REJECTED_TOPIC) {
            job.setCompletedAt(LocalDateTime.now());
        }
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.update(job);
        try {
            placeSocialVideoService.syncSocialJob(job);
        } catch (Exception e) {
            log.warn("Could not link resolved places to social video for job {}: {}",
                    job.getId(), e.getMessage(), e);
        }
        recordAiApiCall(job, request, status);
        if (status == SocialLocationJobStatus.REJECTED_TOPIC) {
            recordTopicViolation(job, request.getError());
        } else if (status == SocialLocationJobStatus.REJECTED_DURATION) {
            audit(job.getUserId(), job.getId(), job.getSourceUrl(), "REJECTED_DURATION",
                    job.getErrorCode(), Map.of(
                            "videoDurationSeconds", job.getVideoDurationSeconds() == null ? 0 : job.getVideoDurationSeconds(),
                            "maxDurationSeconds", job.getMaxDurationSeconds()));
        } else if (status == SocialLocationJobStatus.COMPLETED) {
            boolean itineraryStarted = false;
            try {
                itineraryStarted = completionService.handleCompleted(job, callbackResult);
            } catch (Exception e) {
                log.warn("Could not persist completed social job {} side effects: {}",
                        job.getId(), e.getMessage(), e);
            }
            try {
                placeImportJobService.createFromSocialJobs(
                        job.getUserId(),
                        CreateSocialPlaceImportJobRequest.builder()
                                .socialJobIds(List.of(job.getId()))
                                .maxReviews(5)
                                .build());
            } catch (Exception e) {
                log.warn("Could not queue automatic place import for social job {}: {}",
                        job.getId(), e.getMessage(), e);
            }
            if (!itineraryStarted) {
                try {
                    notifyExtractionCompleted(job, callbackResult);
                } catch (Exception e) {
                    log.warn("Could not notify completion for social job {}: {}", job.getId(), e.getMessage(), e);
                }
            }
        }
        log.info("Social location callback processed: job_id={} python_job_id={} status={}",
                job.getId(), job.getPythonJobId(), job.getStatus());
        return toResponse(job);
    }

    private void notifyExtractionCompleted(SocialLocationJob job, JsonNode result) {
        int placeCount = extractedPlaceCount(result);
        String platformName = platformDisplayName(job.getPlatform());
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("socialJobId", job.getId().toString());
        data.put("placeCount", Integer.toString(placeCount));
        data.put("platform", job.getPlatform());
        data.put("platformName", platformName);
        data.put("sourceUrl", job.getSourceUrl());
        data.put("deepLink", "/profile/saved");
        notificationService.createNotification(
                job.getUserId(),
                null,
                NotificationType.SOCIAL_PLACES_EXTRACTED,
                null,
                null,
                data,
                null
        );
    }

    private int extractedPlaceCount(JsonNode result) {
        if (result == null || result.isNull()) {
            return 0;
        }
        JsonNode candidates = result.path("extraction").path("candidates");
        return candidates.isArray() ? candidates.size() : 0;
    }

    private String platformDisplayName(String platform) {
        if (platform == null) {
            return "Social";
        }
        return switch (platform.trim().toLowerCase(Locale.ROOT)) {
            case "tiktok" -> "TikTok";
            case "instagram" -> "Instagram";
            default -> "Social";
        };
    }

    private String callbackUrl() {
        return internalBaseUrl.replaceAll("/+$", "") + "/v1/api/internal/social-location/jobs/callback";
    }

    private String platformFromUrl(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            if (host == null) return "unknown";
            host = host.toLowerCase(Locale.ROOT);
            if (host.equals("tiktok.com") || host.endsWith(".tiktok.com")) return "tiktok";
            if (host.equals("instagram.com") || host.endsWith(".instagram.com")
                    || host.equals("instagr.am") || host.endsWith(".instagr.am")) return "instagram";
            return "unknown";
        } catch (IllegalArgumentException ignored) {
            return "unknown";
        }
    }

    private String cleanLanguage(String language) {
        return language == null || language.isBlank() ? "vi" : language.trim();
    }

    private SocialLocationJobStatus parseStatus(String status) {
        try {
            return SocialLocationJobStatus.valueOf(status == null ? "" : status.trim().toUpperCase());
        } catch (Exception e) {
            return SocialLocationJobStatus.FAILED;
        }
    }

    private boolean isTerminal(SocialLocationJobStatus status) {
        return status == SocialLocationJobStatus.COMPLETED
                || status == SocialLocationJobStatus.FAILED
                || status == SocialLocationJobStatus.TIMED_OUT
                || status == SocialLocationJobStatus.REJECTED_DURATION
                || status == SocialLocationJobStatus.REJECTED_TOPIC
                || status == SocialLocationJobStatus.DELETED;
    }

    private void applyError(SocialLocationJob job, JsonNode error) {
        if (error == null || error.isNull()) {
            job.setErrorCode(null);
            job.setErrorMessage(null);
            return;
        }
        JsonNode code = error.get("code");
        JsonNode message = error.get("message");
        job.setErrorCode(code != null && !code.isNull() ? code.asText() : "EXTRACTION_FAILED");
        job.setErrorMessage(message != null && !message.isNull() ? message.asText() : error.toString());
    }

    private JsonNode normalizeError(JsonNode error, String fallbackCode) {
        if (error == null || error.isNull()) return null;
        if (error.isObject()) return error;
        return errorNode(fallbackCode, error.asText("Extraction failed"));
    }

    private ObjectNode errorNode(String code, String message) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        return error;
    }

    private String firstNonBlank(String candidate, String fallback) {
        return candidate == null || candidate.isBlank() ? fallback : candidate;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            return null;
        }
    }

    private SocialLocationJobResponse toResponse(SocialLocationJob job) {
        JsonNode result = enrichResultWithPlaceMappings(
                job.getId(), retainOnlyCertainCandidates(parseJson(job.getResultPayload())));
        return SocialLocationJobResponse.builder()
                .id(job.getId())
                .sourceUrl(job.getSourceUrl())
                .platform(job.getPlatform())
                .operation(job.getOperation() == null ? SocialLocationOperation.GEN_ITINERARY : job.getOperation())
                .status(job.getStatus())
                .pythonJobId(job.getPythonJobId())
                .language(job.getLanguage())
                .userTier(job.getUserTier())
                .videoDurationSeconds(job.getVideoDurationSeconds())
                .maxDurationSeconds(job.getMaxDurationSeconds())
                .result(result)
                .aiTripJobId(job.getAiTripJobId())
                .savedSpotCount(job.getSavedSpotCount())
                .errorCode(job.getErrorCode())
                .errorMessage(job.getErrorMessage())
                .attemptCount(job.getAttemptCount())
                .deadlineAt(job.getDeadlineAt())
                .lastHeartbeatAt(job.getLastHeartbeatAt())
                .failureStage(job.getFailureStage())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private void recordAiApiCall(SocialLocationJob job,
                                 SocialLocationJobCallbackRequest callback,
                                 SocialLocationJobStatus jobStatus) {
        JsonNode pipelineResult = callback.getResult();
        JsonNode extraction = pipelineResult == null ? null : pipelineResult.path("extraction");
        boolean hasAiResponse = extraction != null && extraction.isObject() && !extraction.isEmpty();
        JsonNode response = hasAiResponse ? extraction : pipelineResult;

        ObjectNode effectiveRequest = objectMapper.createObjectNode();
        effectiveRequest.put("sourceUrl", job.getSourceUrl());
        effectiveRequest.put("platform", job.getPlatform());
        effectiveRequest.put("language", job.getLanguage());
        effectiveRequest.put("userTier", job.getUserTier());
        effectiveRequest.put("maxDurationSeconds", job.getMaxDurationSeconds());
        effectiveRequest.put("operation", job.getOperation() == null
                ? SocialLocationOperation.GEN_ITINERARY.name()
                : job.getOperation().name());
        effectiveRequest.put("candidatePolicy", CURRENT_CANDIDATE_POLICY);
        effectiveRequest.put("attempt", job.getAttemptCount() == null ? 0 : job.getAttemptCount());
        JsonNode originalRequest = parseJson(job.getRequestPayload());
        if (originalRequest != null) {
            effectiveRequest.set("originalRequest", originalRequest);
        }

        LocalDateTime measuredAt = job.getCompletedAt() == null ? LocalDateTime.now() : job.getCompletedAt();
        LocalDateTime completedAt = isTerminal(jobStatus) ? measuredAt : null;
        Long latencyMs = job.getStartedAt() == null
                ? null
                : Math.max(0L, Duration.between(job.getStartedAt(), measuredAt).toMillis());
        int candidateCount = hasAiResponse && extraction.path("candidates").isArray()
                ? extraction.path("candidates").size()
                : 0;
        JsonNode usage = hasAiResponse ? extraction.path("usage") : null;

        aiApiCallMapper.upsert(AiApiCall.builder()
                .id(UUID.randomUUID())
                .userId(job.getUserId())
                .feature("SOCIAL_LOCATION")
                .operation("EXTRACT_PLACES")
                .correlationId(job.getId().toString())
                .provider(hasAiResponse ? text(extraction, "provider") : socialConfig.aiProvider())
                .model(hasAiResponse ? text(extraction, "model") : socialConfig.aiModel())
                .status(jobStatus == SocialLocationJobStatus.PROCESSING
                        ? "PROCESSING"
                        : hasAiResponse ? "SUCCEEDED" : jobStatus.name())
                .requestPayload(effectiveRequest.toString())
                .responsePayload(response == null || response.isMissingNode() || response.isNull()
                        ? null : response.toString())
                .errorPayload(callback.getError() == null || callback.getError().isNull()
                        ? null : callback.getError().toString())
                .candidateCount(candidateCount)
                .inputTokens(integer(usage, "inputTokens", "prompt_tokens"))
                .outputTokens(integer(usage, "outputTokens", "completion_tokens"))
                .totalTokens(integer(usage, "totalTokens", "total_tokens"))
                .latencyMs(latencyMs)
                .startedAt(job.getStartedAt())
                .completedAt(completedAt)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private JsonNode attachExistingDatabasePlaces(JsonNode result) {
        if (result == null || !result.isObject()) {
            return result;
        }
        JsonNode copy = result.deepCopy();
        JsonNode extractionCandidates = copy.path("extraction").path("candidates");
        if (!extractionCandidates.isArray()) {
            return copy;
        }
        for (JsonNode candidate : extractionCandidates) {
            JsonNode mapCandidates = candidate.path("mapSearch").path("candidates");
            if (!mapCandidates.isArray()) {
                continue;
            }
            for (JsonNode mapCandidate : mapCandidates) {
                if (!(mapCandidate instanceof ObjectNode objectNode)) {
                    continue;
                }
                Place existing = findExistingPlace(mapCandidate);
                if (existing == null) {
                    continue;
                }
                ObjectNode mapping = objectNode.putObject("placeMapping");
                mapping.put("placeId", existing.getId().toString());
                mapping.put("approvalStatus", "PENDING");
                mapping.put("itemStatus", "SKIPPED_EXISTING");
                mapping.put("source", "EXISTING_DATABASE");
                String imageUrl = placeImageUrl(existing);
                if (imageUrl != null) {
                    mapping.put("thumbnail", imageUrl);
                }
                if (existing.getTitle() != null && !existing.getTitle().isBlank()) {
                    mapping.put("title", existing.getTitle());
                }
            }
        }
        return copy;
    }

    private boolean usesCurrentCandidatePolicy(SocialLocationJob job) {
        JsonNode result = parseJson(job.getResultPayload());
        return result != null
                && CURRENT_CANDIDATE_POLICY.equals(result.path("extraction")
                        .path("candidateValidation")
                        .path("policy")
                        .asText());
    }

    private JsonNode retainOnlyCertainCandidates(JsonNode result) {
        if (result == null || !result.isObject()) {
            return result;
        }
        JsonNode copy = result.deepCopy();
        if (!(copy.path("extraction") instanceof ObjectNode extraction)) {
            return copy;
        }
        ArrayNode retainedCandidates = objectMapper.createArrayNode();
        JsonNode candidates = extraction.path("candidates");
        if (candidates.isArray()) {
            for (JsonNode candidate : candidates) {
                // Resolution confidence is surfaced per candidate. It must never remove a
                // named mention: unresolved spots become editable ACTIVITY rows downstream.
                if (candidate != null && candidate.isObject()
                        && text(candidate, "name", "query") != null) {
                    retainedCandidates.add(candidate);
                }
            }
        }
        extraction.set("candidates", retainedCandidates);
        extraction.put("found", !retainedCandidates.isEmpty());
        extraction.put("needs_confirmation", false);
        return copy;
    }

    private SocialLocationOperation requestedOperation(CreateSocialLocationJobRequest request) {
        return request.getOperation() == null ? SocialLocationOperation.GEN_ITINERARY : request.getOperation();
    }

    private Place findExistingPlace(JsonNode candidate) {
        String googlePlaceId = text(candidate, "placeId", "googlePlaceId");
        if (googlePlaceId != null) {
            Place place = placeMapper.findByPlaceId(googlePlaceId);
            if (place != null) {
                return place;
            }
        }
        String cid = text(candidate, "cid");
        if (cid != null) {
            Place place = placeMapper.findByCid(cid);
            if (place != null) {
                return place;
            }
        }
        java.math.BigDecimal latitude = decimal(candidate, "latitude");
        java.math.BigDecimal longitude = decimal(candidate, "longitude");
        if (latitude != null && longitude != null) {
            return placeMapper.findNearCoordinates(
                    latitude,
                    longitude,
                    java.math.BigDecimal.valueOf(25));
        }
        return null;
    }

    private void enforceRestriction(UUID userId, String sourceUrl) {
        SocialLocationUserRestriction restriction = restrictionRepository.findByUserId(userId).orElse(null);
        if (restriction == null || restriction.getStatus() == SocialLocationRestrictionStatus.ACTIVE) return;
        if (restriction.getStatus() == SocialLocationRestrictionStatus.PERMANENTLY_BLOCKED) {
            audit(userId, null, sourceUrl, "BLOCKED_SUBMISSION", "PERMANENTLY_BLOCKED", null);
            throw new BusinessException(ErrorConstant.SOCIAL_LOCATION_PERMANENTLY_BLOCKED,
                    Map.of("strikeCount", restriction.getStrikeCount()));
        }
        if (restriction.getBlockedUntil() != null && restriction.getBlockedUntil().isAfter(LocalDateTime.now())) {
            SocialLocationUserRestriction escalated = applyStrike(
                    restriction, "SUBMITTED_WHILE_BLOCKED", "User submitted another URL while blocked");
            audit(userId, null, sourceUrl, "BLOCKED_SUBMISSION", "SUBMITTED_WHILE_BLOCKED",
                    Map.of("strikeCount", escalated.getStrikeCount()));
            int code = escalated.getStatus() == SocialLocationRestrictionStatus.PERMANENTLY_BLOCKED
                    ? ErrorConstant.SOCIAL_LOCATION_PERMANENTLY_BLOCKED
                    : ErrorConstant.SOCIAL_LOCATION_TEMPORARILY_BLOCKED;
            throw new BusinessException(code, restrictionData(escalated));
        }
        restriction.setStatus(SocialLocationRestrictionStatus.ACTIVE);
        restriction.setBlockedUntil(null);
        restriction.setUpdatedAt(LocalDateTime.now());
        restrictionRepository.save(restriction);
    }

    private void recordTopicViolation(SocialLocationJob job, JsonNode error) {
        SocialLocationUserRestriction restriction = restrictionRepository.findByUserId(job.getUserId())
                .orElse(SocialLocationUserRestriction.builder()
                        .userId(job.getUserId()).strikeCount(0)
                        .status(SocialLocationRestrictionStatus.ACTIVE)
                        .createdAt(LocalDateTime.now()).build());
        String message = error != null && error.path("message").isTextual()
                ? error.path("message").asText() : "Video is not about travel, food, or a place review";
        SocialLocationUserRestriction updated = applyStrike(restriction, "IRRELEVANT_TOPIC", message);
        audit(job.getUserId(), job.getId(), job.getSourceUrl(), "TOPIC_VIOLATION", "IRRELEVANT_TOPIC",
                Map.of("strikeCount", updated.getStrikeCount(), "restrictionStatus", updated.getStatus().name()));
    }

    private SocialLocationUserRestriction applyStrike(SocialLocationUserRestriction restriction,
                                                       String reasonCode,
                                                       String message) {
        int strikes = (restriction.getStrikeCount() == null ? 0 : restriction.getStrikeCount()) + 1;
        restriction.setStrikeCount(strikes);
        restriction.setReasonCode(reasonCode);
        restriction.setReasonMessage(message);
        restriction.setUpdatedAt(LocalDateTime.now());
        if (restriction.getCreatedAt() == null) restriction.setCreatedAt(LocalDateTime.now());
        if (strikes >= socialConfig.permanentBlockStrikes()) {
            restriction.setStatus(SocialLocationRestrictionStatus.PERMANENTLY_BLOCKED);
            restriction.setBlockedUntil(null);
        } else if (strikes == 1) {
            restriction.setStatus(SocialLocationRestrictionStatus.COOLDOWN);
            restriction.setBlockedUntil(LocalDateTime.now().plusMinutes(socialConfig.firstBlockMinutes()));
        } else {
            restriction.setStatus(SocialLocationRestrictionStatus.TEMPORARILY_BLOCKED);
            restriction.setBlockedUntil(LocalDateTime.now().plusHours(socialConfig.secondBlockHours()));
        }
        restrictionRepository.save(restriction);
        return restriction;
    }

    private Map<String, Object> restrictionData(SocialLocationUserRestriction restriction) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("strikeCount", restriction.getStrikeCount());
        data.put("status", restriction.getStatus().name());
        if (restriction.getBlockedUntil() != null) data.put("blockedUntil", restriction.getBlockedUntil());
        return data;
    }

    private void audit(UUID userId, UUID jobId, String sourceUrl, String eventType,
                       String reasonCode, Map<String, ?> details) {
        restrictionRepository.insertEvent(SocialLocationSubmissionEvent.builder()
                .id(UUID.randomUUID()).userId(userId).jobId(jobId).sourceUrl(sourceUrl)
                .eventType(eventType).reasonCode(reasonCode)
                .details(details == null ? "{}" : toJson(details))
                .createdAt(LocalDateTime.now()).build());
    }

    private JsonNode enrichResultWithPlaceMappings(UUID socialJobId, JsonNode result) {
        if (result == null || !result.isObject()) {
            return result;
        }
        Map<String, PlaceImportJobItem> mappings = placeImportJobMapper.findSocialItemsBySocialJobId(socialJobId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        PlaceImportJobItem::getSourceCandidateKey,
                        Function.identity(),
                        (first, ignored) -> first));
        if (mappings.isEmpty()) {
            return result;
        }

        JsonNode extractionCandidates = result.path("extraction").path("candidates");
        if (!extractionCandidates.isArray()) {
            return result;
        }
        for (JsonNode candidate : extractionCandidates) {
            JsonNode mapCandidates = candidate.path("mapSearch").path("candidates");
            if (!mapCandidates.isArray()) {
                continue;
            }
            for (JsonNode mapCandidate : mapCandidates) {
                if (!(mapCandidate instanceof ObjectNode objectNode)) {
                    continue;
                }
                String key = PlaceImportCandidateKey.of(
                        text(mapCandidate, "placeId"),
                        text(mapCandidate, "cid"),
                        decimal(mapCandidate, "latitude"),
                        decimal(mapCandidate, "longitude"),
                        text(mapCandidate, "title", "name"),
                        text(mapCandidate, "googleMapsLink", "resolvedUrl"));
                PlaceImportJobItem item = mappings.get(key);
                if (item == null) {
                    continue;
                }
                ObjectNode mapping = objectNode.putObject("placeMapping");
                mapping.put("approvalStatus", item.getApprovalStatus().name());
                mapping.put("itemStatus", item.getStatus().name());
                if (item.getErrorMessage() != null && !item.getErrorMessage().isBlank()) {
                    mapping.put("errorMessage", item.getErrorMessage());
                }
                UUID placeId = item.getImportedPlaceId() != null
                        ? item.getImportedPlaceId()
                        : item.getExistingPlaceId();
                if (placeId == null) {
                    continue;
                }
                mapping.put("placeId", placeId.toString());
                Place place = placeMapper.findById(placeId);
                String imageUrl = placeImageUrl(place);
                if (imageUrl != null) {
                    mapping.put("thumbnail", imageUrl);
                }
            }
        }
        return result;
    }

    private String placeImageUrl(Place place) {
        if (place == null) {
            return null;
        }
        if (place.getThumbnail() != null && !place.getThumbnail().isBlank()) {
            return place.getThumbnail();
        }
        JsonNode images = parseJson(place.getImages());
        if (images == null || !images.isArray()) {
            return null;
        }
        for (JsonNode image : images) {
            String url = image.isTextual()
                    ? image.asText()
                    : text(image, "image", "url", "imageUrl");
            if (url != null && !url.isBlank()) {
                return url;
            }
        }
        return null;
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private java.math.BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        try {
            return new java.math.BigDecimal(value.asText());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer integer(JsonNode node, String... fields) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.canConvertToInt()) {
                return value.asInt();
            }
        }
        return null;
    }
}
