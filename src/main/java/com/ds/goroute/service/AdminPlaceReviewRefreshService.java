package com.ds.goroute.service;

import com.ds.goroute.dto.response.PlaceReviewRefreshResponse;

import java.util.UUID;

public interface AdminPlaceReviewRefreshService {
    PlaceReviewRefreshResponse trigger(UUID placeId, int maxReviews);
}
