package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.request.CreateReviewRequest;
import com.ds.goroute.dto.request.UpdateReviewRequest;
import com.ds.goroute.dto.response.PlaceScoreResponse;
import com.ds.goroute.dto.response.ReviewEligibilityResponse;
import com.ds.goroute.dto.response.ReviewScoreResponse;
import com.ds.goroute.dto.response.UserReviewProfileResponse;
import com.ds.goroute.dto.response.UserReviewResponse;
import com.ds.goroute.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/reviews")
@RequiredArgsConstructor
@Slf4j
public class ReviewController extends BaseController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity createReview(
            @Valid @RequestBody CreateReviewRequest request,
            @CurrentUser UUID userId) {
        UserReviewResponse response = reviewService.createReview(userId, request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @GetMapping("/eligibility")
    public ResponseEntity getEligibility(
            @RequestParam(required = false) UUID hotelBookingId,
            @RequestParam(required = false) UUID activityOrderId,
            @CurrentUser UUID userId) {
        ReviewEligibilityResponse response = reviewService.getEligibility(userId, hotelBookingId, activityOrderId);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity updateReview(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReviewRequest request,
            @CurrentUser UUID userId) {
        UserReviewResponse response = reviewService.updateReview(userId, id, request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity deleteReview(
            @PathVariable UUID id,
            @CurrentUser UUID userId) {
        reviewService.deleteReview(userId, id);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    /**
     * One review, for the detail screen a "someone liked/commented on your review"
     * notification opens. Readable signed out, like the place listing it also appears in.
     */
    @GetMapping("/{id}")
    public ResponseEntity getReview(
            @PathVariable UUID id,
            @CurrentUser(required = false) UUID userId) {
        UserReviewResponse response = reviewService.getReview(id, userId);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @GetMapping("/places/{placeId}")
    public ResponseEntity getPlaceReviews(
            @PathVariable UUID placeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser(required = false) UUID userId) {
        List<UserReviewResponse> reviews = reviewService.getPlaceReviews(placeId, userId, page, size);
        return ResponseEntity.ok(ofSucceeded(reviews));
    }

    @GetMapping("/activity-bookings/{activityBookingId}")
    public ResponseEntity getActivityBookingReviews(
            @PathVariable UUID activityBookingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser(required = false) UUID userId) {
        List<UserReviewResponse> reviews = reviewService.getActivityBookingReviews(activityBookingId, userId, page, size);
        return ResponseEntity.ok(ofSucceeded(reviews));
    }

    @GetMapping("/users/me")
    public ResponseEntity getMyReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser UUID userId) {
        List<UserReviewResponse> reviews = reviewService.getUserReviews(userId, page, size);
        return ResponseEntity.ok(ofSucceeded(reviews));
    }

    @GetMapping("/feed")
    public ResponseEntity getFeedReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String randomSeed,
            @CurrentUser(required = false) UUID userId) {
        List<UserReviewResponse> reviews = reviewService.getFeedReviews(userId, page, size, randomSeed);
        return ResponseEntity.ok(ofSucceeded(reviews));
    }

    @PostMapping("/{id}/helpful")
    public ResponseEntity voteHelpful(
            @PathVariable UUID id,
            @CurrentUser UUID userId) {
        UserReviewResponse response = reviewService.voteHelpful(userId, id);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping("/{id}/unhelpful")
    public ResponseEntity voteUnhelpful(
            @PathVariable UUID id,
            @CurrentUser UUID userId) {
        UserReviewResponse response = reviewService.voteUnhelpful(userId, id);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @GetMapping("/places/{placeId}/score")
    public ResponseEntity getPlaceScore(@PathVariable UUID placeId) {
        PlaceScoreResponse response = reviewService.getPlaceScore(placeId);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @GetMapping("/activity-bookings/{activityBookingId}/score")
    public ResponseEntity getActivityBookingScore(@PathVariable UUID activityBookingId) {
        ReviewScoreResponse response = reviewService.getActivityBookingScore(activityBookingId);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @GetMapping("/users/me/profile")
    public ResponseEntity getMyProfile(@CurrentUser UUID userId) {
        UserReviewProfileResponse response = reviewService.getUserProfile(userId);
        return ResponseEntity.ok(ofSucceeded(response));
    }
}
