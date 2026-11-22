package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.FeedGeoFilter;
import com.ds.goroute.dto.FeedPlaceKind;
import com.ds.goroute.dto.request.CreateReviewRequest;
import com.ds.goroute.dto.request.UpdateReviewRequest;
import com.ds.goroute.dto.response.PlaceScoreResponse;
import com.ds.goroute.dto.response.ReviewEligibilityResponse;
import com.ds.goroute.dto.response.ReviewScoreResponse;
import com.ds.goroute.dto.response.UserReviewProfileResponse;
import com.ds.goroute.dto.response.UserReviewResponse;
import com.ds.goroute.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/reviews")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ReviewController extends BaseController {

    /** The app asks for up to 100 of its own reviews at once (the "my reviews" lookup). */
    private static final int MAX_PAGE_SIZE = 100;

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
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @CurrentUser(required = false) UUID userId) {
        List<UserReviewResponse> reviews = reviewService.getPlaceReviews(placeId, userId, page, size);
        return ResponseEntity.ok(ofSucceeded(reviews));
    }

    @GetMapping("/activity-bookings/{activityBookingId}")
    public ResponseEntity getActivityBookingReviews(
            @PathVariable UUID activityBookingId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @CurrentUser(required = false) UUID userId) {
        List<UserReviewResponse> reviews = reviewService.getActivityBookingReviews(activityBookingId, userId, page, size);
        return ResponseEntity.ok(ofSucceeded(reviews));
    }

    @GetMapping("/users/me")
    public ResponseEntity getMyReviews(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @CurrentUser UUID userId) {
        List<UserReviewResponse> reviews = reviewService.getUserReviews(userId, page, size);
        return ResponseEntity.ok(ofSucceeded(reviews));
    }

    /**
     * Photos first, then newest. Each review carries a {@code feedCursor}; sending the last
     * one back as {@code cursor} returns the page after it and ignores {@code page}. Without a
     * cursor, or with {@code randomSeed}, the feed pages by number.
     */
    @GetMapping("/feed")
    public ResponseEntity getFeedReviews(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(required = false) String randomSeed,
            // Scope to an area for the Feed's "near you" tab; without a point, everywhere.
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(defaultValue = "50") BigDecimal radiusKm,
            @RequestParam(required = false) @Size(max = 200) String cursor,
            // The Feed's place-kind chips: PlaceGroup and PlaceSubType codes. A post matches
            // through its catalogue place, a trip through any of its stops.
            @RequestParam(required = false) List<String> placeGroups,
            @RequestParam(required = false) List<String> subTypes,
            @CurrentUser(required = false) UUID userId) {
        List<UserReviewResponse> reviews = reviewService.getFeedReviews(
                userId, FeedGeoFilter.of(latitude, longitude, radiusKm), FeedPlaceKind.of(placeGroups, subTypes),
                page, size, randomSeed, cursor);
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
