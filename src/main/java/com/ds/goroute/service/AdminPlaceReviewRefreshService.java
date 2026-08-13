package com.ds.goroute.service;

import com.ds.goroute.dto.response.PlaceReviewRefreshResponse;

import java.util.UUID;
import java.util.Map;

public interface AdminPlaceReviewRefreshService {
    PlaceReviewRefreshResponse trigger(UUID placeId, int maxReviews);

    PlaceReviewRefreshResponse triggerAllActive();

    Map<String, Object> getStatus(UUID jobId);

    Map<String, Object> cancel(UUID jobId);

    PlaceReviewRefreshResponse rerun(UUID jobId, String mode);
}
