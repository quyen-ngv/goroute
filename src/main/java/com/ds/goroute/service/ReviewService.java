package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateReviewRequest;
import com.ds.goroute.dto.request.UpdateReviewRequest;
import com.ds.goroute.dto.response.PlaceScoreResponse;
import com.ds.goroute.dto.response.UserReviewProfileResponse;
import com.ds.goroute.dto.response.UserReviewResponse;

import java.util.List;
import java.util.UUID;

public interface ReviewService {

    UserReviewResponse createReview(UUID userId, CreateReviewRequest request);

    UserReviewResponse updateReview(UUID userId, UUID reviewId, UpdateReviewRequest request);

    void deleteReview(UUID userId, UUID reviewId);

    List<UserReviewResponse> getPlaceReviews(UUID placeId, UUID currentUserId, int page, int size);

    List<UserReviewResponse> getUserReviews(UUID userId, int page, int size);

    UserReviewResponse voteHelpful(UUID userId, UUID reviewId);

    UserReviewResponse voteUnhelpful(UUID userId, UUID reviewId);

    PlaceScoreResponse getPlaceScore(UUID placeId);

    UserReviewProfileResponse getUserProfile(UUID userId);
}
