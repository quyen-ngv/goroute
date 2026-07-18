package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.CreateSocialLocationJobRequest;
import com.ds.goroute.dto.request.SocialLocationJobCallbackRequest;
import com.ds.goroute.dto.request.CreateSocialPlaceImportJobRequest;
import com.ds.goroute.dto.response.SocialLocationJobResponse;
import com.ds.goroute.entity.SocialLocationJob;
import com.ds.goroute.entity.PlaceImportJobItem;
import com.ds.goroute.mapper.PlaceImportJobMapper;
import com.ds.goroute.mapper.SocialLocationJobMapper;
import com.ds.goroute.service.SocialLocationJobService;
import com.ds.goroute.service.PlaceImportJobService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialLocationJobServiceImpl implements SocialLocationJobService {

    private final SocialLocationJobMapper jobMapper;
    private final PlaceImportJobMapper placeImportJobMapper;
    private final ScrapeServiceClient scrapeServiceClient;
    private final ObjectMapper objectMapper;
    private final PlaceImportJobService placeImportJobService;

    @Value("${goroute.internal.public-base-url:http://goroute-app:8080}")
    private String internalBaseUrl;

    @Override
    public SocialLocationJobResponse create(UUID userId, CreateSocialLocationJobRequest request) {
        String sourceUrl = request.getUrl().trim();
        String platform = platformFromUrl(sourceUrl);
        if ("unknown".equals(platform)) {
            throw new IllegalArgumentException("URL must be a TikTok or Instagram URL");
        }
        String sourceKey = SocialLocationSourceKey.fromUrl(sourceUrl);
        SocialLocationJob reusableJob = jobMapper.findReusableByUserIdAndSourceKey(userId, sourceKey);
        if (reusableJob != null) {
            return toResponse(reusableJob);
        }

        LocalDateTime now = LocalDateTime.now();
        SocialLocationJob job = SocialLocationJob.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .sourceUrl(sourceUrl)
                .sourceKey(sourceKey)
                .platform(platform)
                .status(SocialLocationJobStatus.QUEUED)
                .language(cleanLanguage(request.getLanguage()))
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

        ScrapeSocialLocationJobResponse trigger = scrapeServiceClient.triggerSocialLocationJob(
                ScrapeSocialLocationJobRequest.builder()
                        .url(sourceUrl)
                        .language(job.getLanguage())
                        .callbackUrl(callbackUrl())
                        .gorouteJobId(job.getId())
                        .maxAudioSeconds(request.getMaxAudioSeconds())
                        .maxFrames(request.getMaxFrames())
                        .frameIntervalSeconds(request.getFrameIntervalSeconds())
                        .imageMaxWidth(request.getImageMaxWidth())
                        .imageJpegQuality(request.getImageJpegQuality())
                        .maxCandidates(request.getMaxCandidates())
                        .includeMapSearch(request.getIncludeMapSearch())
                        .mapSearchLimit(request.getMapSearchLimit())
                        .headless(request.getHeadless())
                        .build()
        );

        if (trigger == null || trigger.getJobId() == null || trigger.getJobId().isBlank()) {
            job.setStatus(SocialLocationJobStatus.FAILED);
            job.setErrorCode("PYTHON_TRIGGER_FAILED");
            job.setErrorMessage("Python social-location job trigger failed");
            job.setCompletedAt(LocalDateTime.now());
        } else {
            job.setStatus(SocialLocationJobStatus.PROCESSING);
            job.setPythonJobId(trigger.getJobId());
            job.setStartedAt(LocalDateTime.now());
        }
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.update(job);
        return toResponse(job);
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

        SocialLocationJobStatus status = parseStatus(request.getStatus());
        job.setStatus(status);
        job.setPythonJobId(firstNonBlank(request.getPythonJobId(), job.getPythonJobId()));
        job.setSourceUrl(firstNonBlank(request.getSourceUrl(), job.getSourceUrl()));
        job.setPlatform(firstNonBlank(request.getPlatform(), job.getPlatform()));
        job.setResultPayload(request.getResult() != null && !request.getResult().isNull()
                ? request.getResult().toString()
                : job.getResultPayload());
        applyError(job, request.getError());
        if (status == SocialLocationJobStatus.COMPLETED || status == SocialLocationJobStatus.FAILED) {
            job.setCompletedAt(LocalDateTime.now());
        }
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.update(job);
        if (status == SocialLocationJobStatus.COMPLETED) {
            try {
                placeImportJobService.createFromSocialJobs(
                        job.getUserId(),
                        CreateSocialPlaceImportJobRequest.builder()
                                .socialJobIds(List.of(job.getId()))
                                .maxReviews(5)
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
        String lower = url.toLowerCase();
        if (lower.contains("tiktok.com")) {
            return "tiktok";
        }
        if (lower.contains("instagram.com") || lower.contains("instagr.am")) {
            return "instagram";
        }
        return "unknown";
    }

    private String cleanLanguage(String language) {
        return language == null || language.isBlank() ? "vi" : language.trim();
    }

    private SocialLocationJobStatus parseStatus(String status) {
        try {
            return SocialLocationJobStatus.valueOf(status);
        } catch (Exception e) {
            return SocialLocationJobStatus.FAILED;
        }
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
                .result(result)
                .errorCode(job.getErrorCode())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
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
                mapping.put("placeId", item.getImportedPlaceId() != null
                        ? item.getImportedPlaceId().toString()
                        : item.getExistingPlaceId().toString());
                mapping.put("approvalStatus", item.getApprovalStatus().name());
                mapping.put("itemStatus", item.getStatus().name());
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
