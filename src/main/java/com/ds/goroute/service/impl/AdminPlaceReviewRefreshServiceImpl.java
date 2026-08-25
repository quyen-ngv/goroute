package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.PlaceReviewRefreshResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.AdminPlaceReviewRefreshService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.thirdparty.scrape.ScrapeJobTriggerResponse;
import com.ds.goroute.thirdparty.scrape.ScrapePlaceReviewRefreshJobRequest;
import com.ds.goroute.thirdparty.scrape.ScrapeServiceClient;
import com.ds.goroute.type.PlaceVisibilityStatus;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.PlaceReviewRefreshRerunMode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminPlaceReviewRefreshServiceImpl implements AdminPlaceReviewRefreshService {

    private final PlaceRepository placeRepository;
    private final ScrapeServiceClient scrapeServiceClient;
    private final BusinessConfigService businessConfigService;

    @Value("${goroute.internal.public-base-url:http://goroute-app:8080}")
    private String internalBaseUrl;

    @Override
    public PlaceReviewRefreshResponse trigger(UUID placeId, Integer maxReviews) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new IllegalArgumentException("Place not found"));
        if (place.getVisibilityStatus() != PlaceVisibilityStatus.ACTIVE) {
            throw new IllegalArgumentException("Activate the place before refreshing reviews");
        }
        if (place.getGoogleMapsLink() == null || place.getGoogleMapsLink().isBlank()) {
            throw new IllegalArgumentException("Place has no Google Maps link");
        }

        int resolvedMaxReviews = maxReviews == null
                ? businessConfigService.getInt(BusinessConfigKey.PLACE_REVIEW_REFRESH_MAX_REVIEWS)
                : maxReviews;
        if (!BusinessConfigKey.PLACE_REVIEW_REFRESH_MAX_REVIEWS.accepts(resolvedMaxReviews)) {
            throw new IllegalArgumentException("maxReviews must be between 1 and 200");
        }

        return triggerJob(ScrapePlaceReviewRefreshJobRequest.builder()
                .placeId(placeId)
                .maxReviews(resolvedMaxReviews)
                .headless(true)
                .continueOnError(false)
                .build());
    }

    @Override
    public PlaceReviewRefreshResponse triggerAllActive() {
        return triggerJob(ScrapePlaceReviewRefreshJobRequest.builder()
                .headless(true)
                .continueOnError(true)
                .build());
    }

    @Override
    public Map<String, Object> getStatus(UUID jobId) {
        Map<String, Object> status = scrapeServiceClient.pollJobData(jobId.toString());
        if (status == null) {
            throw new IllegalArgumentException("Review refresh job not found or unavailable");
        }
        return status;
    }

    @Override
    public Map<String, Object> cancel(UUID jobId) {
        if (!scrapeServiceClient.cancelJob(jobId.toString())) {
            throw new IllegalArgumentException("Could not stop review refresh job");
        }
        return Map.of("jobId", jobId, "status", "cancellation_requested");
    }

    @Override
    public PlaceReviewRefreshResponse rerun(UUID jobId, PlaceReviewRefreshRerunMode mode) {
        ScrapeJobTriggerResponse trigger = scrapeServiceClient.rerunPlaceReviewRefreshJob(
                jobId.toString(), mode.name());
        if (trigger == null || trigger.getJobId() == null || trigger.getJobId().isBlank()) {
            throw new IllegalArgumentException("Could not rerun review refresh job");
        }
        return PlaceReviewRefreshResponse.builder()
                .jobId(trigger.getJobId())
                .status(trigger.getStatus())
                .pollUrl(trigger.getPollUrl())
                .build();
    }

    private PlaceReviewRefreshResponse triggerJob(ScrapePlaceReviewRefreshJobRequest request) {
        String baseUrl = internalBaseUrl.replaceAll("/+$", "");
        request.setPlacesUrl(baseUrl + "/v1/api/places");
        request.setReviewRefreshUrl(baseUrl + "/v1/api/place-reviews");
        ScrapeJobTriggerResponse trigger = scrapeServiceClient.triggerPlaceReviewRefreshJob(request);
        if (trigger == null || trigger.getJobId() == null || trigger.getJobId().isBlank()) {
            throw new IllegalArgumentException("Could not start review refresh job");
        }
        return PlaceReviewRefreshResponse.builder()
                .jobId(trigger.getJobId())
                .status(trigger.getStatus())
                .pollUrl(trigger.getPollUrl())
                .build();
    }
}
