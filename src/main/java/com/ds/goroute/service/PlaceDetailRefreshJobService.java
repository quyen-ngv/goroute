package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreatePlaceDetailRefreshJobRequest;
import com.ds.goroute.dto.request.PlaceDetailRefreshJobEventRequest;
import com.ds.goroute.dto.response.PlaceImportJobResponse;
import com.ds.goroute.entity.PlaceImportJob;
import com.ds.goroute.mapper.PlaceImportJobMapper;
import com.ds.goroute.thirdparty.scrape.ScrapeJobTriggerResponse;
import com.ds.goroute.thirdparty.scrape.ScrapePlaceDetailRefreshJobRequest;
import com.ds.goroute.thirdparty.scrape.ScrapeServiceClient;
import com.ds.goroute.type.PlaceImportJobStatus;
import com.ds.goroute.type.PlaceImportSourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlaceDetailRefreshJobService {
    private final PlaceImportJobMapper jobMapper;
    private final ScrapeServiceClient scrapeServiceClient;
    private final ObjectMapper objectMapper;

    @Value("${goroute.internal.public-base-url:http://goroute-app:8080}")
    private String publicBaseUrl;

    @Value("${scrape.service.callback-token:}")
    private String callbackToken;

    public PlaceImportJobResponse trigger(CreatePlaceDetailRefreshJobRequest request) {
        PlaceImportJob active = jobMapper.findActivePlaceDetailRefreshJob();
        if (active != null) {
            throw new IllegalStateException("A place detail refresh job is already active: " + active.getId());
        }

        LocalDateTime now = LocalDateTime.now();
        PlaceImportJob job = PlaceImportJob.builder()
                .id(UUID.randomUUID())
                .sourceType(PlaceImportSourceType.PLACE_DETAILS_REFRESH)
                .status(PlaceImportJobStatus.QUEUED)
                .maxReviews(0)
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
                .selectedReviews(0)
                .minReviewCount(0)
                .minAdjustedRating(BigDecimal.ZERO)
                .requestPayload(toJson(request))
                .createdAt(now)
                .updatedAt(now)
                .build();
        jobMapper.insertJob(job);

        String callbackUrl = publicBaseUrl.replaceAll("/+$", "")
                + "/v1/api/internal/place-import-jobs/place-details-refresh/events";
        ScrapeJobTriggerResponse trigger = scrapeServiceClient.triggerPlaceDetailRefreshJob(
                ScrapePlaceDetailRefreshJobRequest.builder()
                        .gorouteJobId(job.getId().toString())
                        .callbackUrl(callbackUrl)
                        .callbackToken(callbackToken)
                        .placeId(request.getPlaceId())
                        .maxPlaces(request.getMaxPlaces())
                        .headless(!Boolean.FALSE.equals(request.getHeadless()))
                        .continueOnError(!Boolean.FALSE.equals(request.getContinueOnError()))
                        .build());

        PlaceImportJob persisted = jobMapper.findJobById(job.getId());
        if (persisted != null) {
            job = persisted;
        }
        if (trigger == null || trigger.getJobId() == null || trigger.getJobId().isBlank()) {
            if (!isTerminal(job.getStatus())) {
                job.setStatus(PlaceImportJobStatus.FAILED);
                job.setErrorMessage("Python place detail refresh job could not be started");
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
        return response(job);
    }

    @Transactional
    public void acceptEvent(PlaceDetailRefreshJobEventRequest event) {
        PlaceImportJob job = requireJob(event.getJobId());
        if (job.getPythonJobId() != null && !job.getPythonJobId().equals(event.getPythonJobId())) {
            throw new IllegalArgumentException("Python job id does not match");
        }
        if (job.getPythonJobId() == null) {
            job.setPythonJobId(event.getPythonJobId());
        }

        if (event.getDatabaseCount() != null) job.setTotalItems(event.getDatabaseCount());
        if (event.getEligibleCount() != null) job.setEligibleCount(event.getEligibleCount());
        if (event.getProcessedCount() != null) job.setProcessedCount(event.getProcessedCount());
        if (event.getSuccessCount() != null) {
            job.setCompletedCount(event.getSuccessCount());
            job.setImportedCount(event.getSuccessCount());
        }
        if (event.getFailedCount() != null) job.setFailedCount(event.getFailedCount());
        if (event.getCurrentPlaceId() != null) job.setCurrentRegionCode(event.getCurrentPlaceId().toString());
        if (event.getCurrentTitle() != null) job.setCurrentRegionName(event.getCurrentTitle());

        String type = event.getEventType().trim().toUpperCase(Locale.ROOT);
        if (job.getStatus() == PlaceImportJobStatus.CANCELLED && !"JOB_CANCELLED".equals(type)) {
            return;
        }
        if ("JOB_COMPLETED".equals(type)) {
            job.setStatus(PlaceImportJobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
        } else if ("JOB_FAILED".equals(type)) {
            job.setStatus(PlaceImportJobStatus.FAILED);
            job.setErrorMessage(event.getErrorMessage());
            job.setCompletedAt(LocalDateTime.now());
        } else if ("JOB_CANCELLED".equals(type)) {
            job.setStatus(PlaceImportJobStatus.CANCELLED);
            job.setCompletedAt(LocalDateTime.now());
        } else if (job.getStatus() != PlaceImportJobStatus.CANCELLED) {
            job.setStatus(PlaceImportJobStatus.PROCESSING);
            if (job.getStartedAt() == null) job.setStartedAt(LocalDateTime.now());
        }
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateJob(job);
    }

    @Transactional
    public PlaceImportJobResponse cancel(UUID jobId) {
        PlaceImportJob job = requireJob(jobId);
        if (!isTerminal(job.getStatus())) {
            job.setCancelRequested(true);
            if (job.getPythonJobId() != null) {
                scrapeServiceClient.cancelJob(job.getPythonJobId());
            }
            job.setStatus(PlaceImportJobStatus.CANCELLED);
            job.setCompletedAt(LocalDateTime.now());
            job.setUpdatedAt(LocalDateTime.now());
            jobMapper.updateJob(job);
        }
        return response(job);
    }

    public PlaceImportJobResponse get(UUID jobId) {
        return response(requireJob(jobId));
    }

    public PlaceImportJobResponse retry(UUID jobId) {
        PlaceImportJob oldJob = requireJob(jobId);
        if (oldJob.getStatus() != PlaceImportJobStatus.FAILED
                && oldJob.getStatus() != PlaceImportJobStatus.CANCELLED) {
            throw new IllegalStateException("Only failed or cancelled place detail refresh jobs can be retried");
        }
        CreatePlaceDetailRefreshJobRequest request;
        try {
            request = objectMapper.readValue(oldJob.getRequestPayload(), CreatePlaceDetailRefreshJobRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Stored place detail refresh configuration is invalid", e);
        }
        return trigger(request);
    }

    private PlaceImportJob requireJob(UUID jobId) {
        PlaceImportJob job = jobMapper.findJobById(jobId);
        if (job == null || job.getSourceType() != PlaceImportSourceType.PLACE_DETAILS_REFRESH) {
            throw new IllegalArgumentException("Place detail refresh job not found");
        }
        return job;
    }

    private PlaceImportJobResponse response(PlaceImportJob job) {
        CreatePlaceDetailRefreshJobRequest config = parseConfiguration(job.getRequestPayload());
        return PlaceImportJobResponse.builder()
                .id(job.getId())
                .sourceType(job.getSourceType())
                .status(job.getStatus())
                .pythonJobId(job.getPythonJobId())
                .totalItems(job.getTotalItems())
                .eligibleCount(job.getEligibleCount())
                .processedCount(job.getProcessedCount())
                .completedCount(job.getCompletedCount())
                .importedCount(job.getImportedCount())
                .failedCount(job.getFailedCount())
                .cancelRequested(job.getCancelRequested())
                .refreshPlaceId(config == null ? null : config.getPlaceId())
                .maxPlaces(config == null ? null : config.getMaxPlaces())
                .headless(config == null ? null : config.getHeadless())
                .continueOnError(config == null ? null : config.getContinueOnError())
                .currentPlaceId(job.getCurrentRegionCode())
                .currentPlaceTitle(job.getCurrentRegionName())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private CreatePlaceDetailRefreshJobRequest parseConfiguration(String payload) {
        if (payload == null || payload.isBlank()) return null;
        try {
            return objectMapper.readValue(payload, CreatePlaceDetailRefreshJobRequest.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private boolean isTerminal(PlaceImportJobStatus status) {
        return status == PlaceImportJobStatus.COMPLETED
                || status == PlaceImportJobStatus.FAILED
                || status == PlaceImportJobStatus.CANCELLED;
    }
}
