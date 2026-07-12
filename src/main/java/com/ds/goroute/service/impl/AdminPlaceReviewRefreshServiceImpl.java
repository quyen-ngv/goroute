package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.PlaceReviewRefreshResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.AdminPlaceReviewRefreshService;
import com.ds.goroute.thirdparty.scrape.ScrapeJobTriggerResponse;
import com.ds.goroute.thirdparty.scrape.ScrapePlaceImportJobRequest;
import com.ds.goroute.thirdparty.scrape.ScrapeServiceClient;
import com.ds.goroute.type.PlaceVisibilityStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminPlaceReviewRefreshServiceImpl implements AdminPlaceReviewRefreshService {

    private final PlaceRepository placeRepository;
    private final ScrapeServiceClient scrapeServiceClient;

    @Value("${goroute.internal.public-base-url:http://goroute-app:8080}")
    private String publicBaseUrl;

    @Override
    public PlaceReviewRefreshResponse trigger(UUID placeId, int maxReviews) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new IllegalArgumentException("Place not found"));
        if (place.getVisibilityStatus() != PlaceVisibilityStatus.ACTIVE) {
            throw new IllegalArgumentException("Activate the place before refreshing reviews");
        }
        if (place.getGoogleMapsLink() == null || place.getGoogleMapsLink().isBlank()) {
            throw new IllegalArgumentException("Place has no Google Maps link");
        }

        ScrapeJobTriggerResponse trigger = scrapeServiceClient.triggerPlaceScrapeImport(
                ScrapePlaceImportJobRequest.builder()
                        .url(place.getGoogleMapsLink())
                        .maxReviews(Math.min(Math.max(maxReviews, 1), 5))
                        .maxScrolls(30)
                        .headless(true)
                        .visibilityStatus(PlaceVisibilityStatus.ACTIVE.name())
                        .importConfig(ScrapePlaceImportJobRequest.ImportConfig.builder()
                                .enabled(true)
                                .url(publicBaseUrl.replaceAll("/+$", "") + "/v1/api/places/import")
                                .method("POST")
                                .headers(Map.of())
                                .build())
                        .build());
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
