package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.CreateSocialLocationJobRequest;
import com.ds.goroute.dto.request.SocialLocationJobCallbackRequest;
import com.ds.goroute.dto.response.SocialLocationJobResponse;
import com.ds.goroute.entity.SocialLocationJob;
import com.ds.goroute.mapper.SocialLocationJobMapper;
import com.ds.goroute.service.SocialLocationJobService;
import com.ds.goroute.thirdparty.scrape.ScrapeServiceClient;
import com.ds.goroute.thirdparty.scrape.ScrapeSocialLocationJobRequest;
import com.ds.goroute.thirdparty.scrape.ScrapeSocialLocationJobResponse;
import com.ds.goroute.type.SocialLocationJobStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialLocationJobServiceImpl implements SocialLocationJobService {

    private final SocialLocationJobMapper jobMapper;
    private final ScrapeServiceClient scrapeServiceClient;
    private final ObjectMapper objectMapper;

    @Value("${goroute.internal.public-base-url:http://goroute-app:8080}")
    private String internalBaseUrl;

    @Override
    public SocialLocationJobResponse create(UUID userId, CreateSocialLocationJobRequest request) {
        String sourceUrl = request.getUrl().trim();
        String platform = platformFromUrl(sourceUrl);
        if ("unknown".equals(platform)) {
            throw new IllegalArgumentException("URL must be a TikTok or Instagram URL");
        }

        LocalDateTime now = LocalDateTime.now();
        SocialLocationJob job = SocialLocationJob.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .sourceUrl(sourceUrl)
                .platform(platform)
                .status(SocialLocationJobStatus.QUEUED)
                .language(cleanLanguage(request.getLanguage()))
                .requestPayload(toJson(request))
                .createdAt(now)
                .updatedAt(now)
                .build();
        jobMapper.insert(job);

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
        return SocialLocationJobResponse.builder()
                .id(job.getId())
                .sourceUrl(job.getSourceUrl())
                .platform(job.getPlatform())
                .status(job.getStatus())
                .pythonJobId(job.getPythonJobId())
                .language(job.getLanguage())
                .result(parseJson(job.getResultPayload()))
                .errorCode(job.getErrorCode())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
