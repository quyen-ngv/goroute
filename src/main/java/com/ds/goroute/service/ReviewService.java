package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateReviewRequest;
import com.ds.goroute.dto.request.UpdateReviewRequest;
import com.ds.goroute.dto.response.PlaceScoreResponse;
import com.ds.goroute.dto.response.ReviewEligibilityResponse;
import com.ds.goroute.dto.response.ReviewScoreResponse;
import com.ds.goroute.dto.response.UserReviewProfileResponse;
import com.ds.goroute.dto.response.UserReviewResponse;

import java.util.List;
import java.util.UUID;

public interface ReviewService {

    UserReviewResponse createReview(UUID userId, CreateReviewRequest request);

    /**
     * Can {@code userId} review the given marketplace booking? Exactly one of the two ids must be set.
     */
    ReviewEligibilityResponse getEligibility(UUID userId, UUID hotelBookingId, UUID activityOrderId);

    UserReviewResponse updateReview(UUID userId, UUID reviewId, UpdateReviewRequest request);

    void deleteReview(UUID userId, UUID reviewId);

    /**
     * One review on its own, for the detail screen a social notification opens.
     * Every other read is scoped to a place, a booking or an author, none of which a
     * "someone liked your review" payload names.
     */
    UserReviewResponse getReview(UUID reviewId, UUID currentUserId);

    List<UserReviewResponse> getPlaceReviews(UUID placeId, UUID currentUserId, int page, int size);

    List<UserReviewResponse> getActivityBookingReviews(UUID activityBookingId, UUID currentUserId, int page, int size);

    List<UserReviewResponse> getUserReviews(UUID userId, int page, int size);

    List<UserReviewResponse> getUserReviewsForProfile(UUID targetUserId, UUID viewerId, int page, int size);

    List<UserReviewResponse> getFeedReviews(UUID currentUserId, int page, int size, String randomSeed);

    UserReviewResponse voteHelpful(UUID userId, UUID reviewId);

    UserReviewResponse voteUnhelpful(UUID userId, UUID reviewId);

    PlaceScoreResponse getPlaceScore(UUID placeId);

    ReviewScoreResponse getActivityBookingScore(UUID activityBookingId);

    UserReviewProfileResponse getUserProfile(UUID userId);
}
