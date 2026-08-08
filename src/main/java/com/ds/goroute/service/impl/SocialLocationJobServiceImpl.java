package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.CreateSocialLocationJobRequest;
import com.ds.goroute.dto.request.SocialLocationJobCallbackRequest;
import com.ds.goroute.dto.request.CreateSocialPlaceImportJobRequest;
import com.ds.goroute.dto.response.SocialLocationJobResponse;
import com.ds.goroute.entity.SocialLocationJob;
import com.ds.goroute.entity.PlaceImportJobItem;
import com.ds.goroute.mapper.PlaceImportJobMapper;
import com.ds.goroute.mapper.PlaceMapper;
import com.ds.goroute.mapper.SocialLocationJobMapper;
import com.ds.goroute.entity.Place;
import com.ds.goroute.service.SocialLocationJobService;
import com.ds.goroute.service.PlaceImportJobService;
import com.ds.goroute.service.SocialLocationConfigService;
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
import com.ds.goroute.util.SocialLocationSourceKey;
import com.ds.goroute.util.PlaceImportCandidateKey;
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

    private final SocialLocationJobMapper jobMapper;
    private final PlaceImportJobMapper placeImportJobMapper;
    private final PlaceMapper placeMapper;
    private final ScrapeServiceClient scrapeServiceClient;
    private final ObjectMapper objectMapper;
    private final PlaceImportJobService placeImportJobService;
    private final SocialLocationConfigService socialConfig;
    private final AiTripRepository aiTripRepository;
    private final SocialLocationRestrictionRepository restrictionRepository;

    @Value("${goroute.internal.public-base-url:http://goroute-app:8080}")
    private String internalBaseUrl;

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
        SocialLocationJob reusableJob = jobMapper.findReusableByUserIdAndSourceKey(userId, sourceKey);
        if (reusableJob != null) {
            return toResponse(reusableJob);
        }

        if (jobMapper.countCreatedByUserSince(userId, LocalDate.now().atStartOfDay()) >= socialConfig.dailyJobLimit()) {
            audit(userId, null, sourceUrl, "REJECTED_DAILY_LIMIT", "DAILY_LIMIT_REACHED", null);
            throw new BusinessException(ErrorConstant.SOCIAL_LOCATION_DAILY_LIMIT_REACHED,
                    Map.of("dailyLimit", socialConfig.dailyJobLimit()));
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
                .status(SocialLocationJobStatus.QUEUED)
                .language(cleanLanguage(request.getLanguage()))
                .userTier(userTier)
                .maxDurationSeconds(maxDurationSeconds)
                .requestPayload(toJson(request))
                .createdAt(now)
                .updatedAt(now)
                .build();
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
                Map.of("tier", userTier, "maxDurationSeconds", maxDurationSeconds));
        return toResponse(job);
    }

    @Override
    @Transactional
    public void dispatchQueuedJobs() {
        if (!jobMapper.tryDispatchLock()) return;
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
                        .gorouteJobId(job.getId())
                        .maxDurationSeconds(job.getMaxDurationSeconds())
                        .maxAudioSeconds(job.getMaxDurationSeconds())
                        .maxFrames(Math.min(maxFrames, 100))
                        .frameIntervalSeconds(interval)
                        .imageMaxWidth(socialConfig.imageMaxWidth())
                        .imageJpegQuality(socialConfig.imageJpegQuality())
                        .maxCandidates(1)
                        .aiProvider(socialConfig.aiProvider())
                        .aiModel(socialConfig.aiModel())
                        .aiBaseUrl(socialConfig.aiBaseUrl())
                        .includeMapSearch(true)
                        .mapSearchLimit(1)
                        .headless(true)
                        .build());

        if (trigger == null || trigger.getJobId() == null || trigger.getJobId().isBlank()) {
            job.setStatus(SocialLocationJobStatus.FAILED);
            job.setErrorCode("PYTHON_TRIGGER_FAILED");
            job.setErrorMessage("Python social-location job trigger failed");
            job.setCompletedAt(LocalDateTime.now());
        } else {
            SocialLocationJob latest = jobMapper.findById(job.getId());
            if (latest != null && isTerminal(latest.getStatus())) return;
            job.setStatus(SocialLocationJobStatus.PROCESSING);
            job.setPythonJobId(trigger.getJobId());
            job.setStartedAt(LocalDateTime.now());
        }
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.update(job);
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
        job.setResultPayload(request.getResult() != null && !request.getResult().isNull()
                ? request.getResult().toString()
                : job.getResultPayload());
        JsonNode duration = request.getResult() == null ? null : request.getResult().path("metadata").get("duration");
        if (duration != null && duration.canConvertToInt()) {
            job.setVideoDurationSeconds(duration.asInt());
        }
        applyError(job, request.getError());
        if (status == SocialLocationJobStatus.COMPLETED
                || status == SocialLocationJobStatus.FAILED
                || status == SocialLocationJobStatus.REJECTED_DURATION
                || status == SocialLocationJobStatus.REJECTED_TOPIC) {
            job.setCompletedAt(LocalDateTime.now());
        }
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.update(job);
        if (status == SocialLocationJobStatus.REJECTED_TOPIC) {
            recordTopicViolation(job, request.getError());
        } else if (status == SocialLocationJobStatus.REJECTED_DURATION) {
            audit(job.getUserId(), job.getId(), job.getSourceUrl(), "REJECTED_DURATION",
                    job.getErrorCode(), Map.of(
                            "videoDurationSeconds", job.getVideoDurationSeconds() == null ? 0 : job.getVideoDurationSeconds(),
                            "maxDurationSeconds", job.getMaxDurationSeconds()));
        } else if (status == SocialLocationJobStatus.COMPLETED) {
            try {
                placeImportJobService.createFromSocialJobs(
                        job.getUserId(),
                        CreateSocialPlaceImportJobRequest.builder()
                                .socialJobIds(List.of(job.getId()))
                                .maxReviews(1)
                                .limit(50)
                                .build());
            } catch (Exception e) {
                log.warn("Could not queue automatic place import for social job {}: {}",
                        job.getId(), e.getMessage());
            }
        }
        log.info("Social location callback processed: job_id={} python_job_id={} status={}",
                job.getId(), job.getPythonJobId(), job.getStatus());
        return toResponse(job);
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
                || status == SocialLocationJobStatus.REJECTED_DURATION
                || status == SocialLocationJobStatus.REJECTED_TOPIC;
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
        JsonNode result = enrichResultWithPlaceMappings(job.getId(), parseJson(job.getResultPayload()));
        return SocialLocationJobResponse.builder()
                .id(job.getId())
                .sourceUrl(job.getSourceUrl())
                .platform(job.getPlatform())
                .status(job.getStatus())
                .pythonJobId(job.getPythonJobId())
                .language(job.getLanguage())
                .userTier(job.getUserTier())
                .videoDurationSeconds(job.getVideoDurationSeconds())
                .maxDurationSeconds(job.getMaxDurationSeconds())
                .result(result)
                .errorCode(job.getErrorCode())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
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
                .filter(item -> item.getStatus() == PlaceImportJobItemStatus.COMPLETED
                        || item.getStatus() == PlaceImportJobItemStatus.SKIPPED_EXISTING)
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
                UUID placeId = item.getImportedPlaceId() != null
                        ? item.getImportedPlaceId()
                        : item.getExistingPlaceId();
                if (placeId == null) {
                    continue;
                }
                mapping.put("placeId", placeId.toString());
                mapping.put("approvalStatus", item.getApprovalStatus().name());
                mapping.put("itemStatus", item.getStatus().name());
                Place place = placeMapper.findById(placeId);
                if (place != null && place.getThumbnail() != null && !place.getThumbnail().isBlank()) {
                    mapping.put("thumbnail", place.getThumbnail());
                }
            }
        }
        return result;
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
}
