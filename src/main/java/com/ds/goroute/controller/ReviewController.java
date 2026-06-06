package com.ds.goroute.controller;

import com.ds.goroute.dto.request.CreateReviewRequest;
import com.ds.goroute.dto.request.UpdateReviewRequest;
import com.ds.goroute.dto.response.PlaceScoreResponse;
import com.ds.goroute.dto.response.UserReviewProfileResponse;
import com.ds.goroute.dto.response.UserReviewResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Reviews", description = "User review management APIs")
public class ReviewController extends BaseService {

    private final ReviewService reviewService;

    @PostMapping
    @Operation(summary = "Create a new review")
    public ResponseEntity createReview(
            @Valid @RequestBody CreateReviewRequest request,
            @RequestAttribute("userId") UUID userId) {
        UserReviewResponse response = reviewService.createReview(userId, request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing review")
    public ResponseEntity updateReview(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReviewRequest request,
            @RequestAttribute("userId") UUID userId) {
        UserReviewResponse response = reviewService.updateReview(userId, id, request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a review")
    public ResponseEntity deleteReview(
            @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId) {
        reviewService.deleteReview(userId, id);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @GetMapping("/places/{placeId}")
    @Operation(summary = "Get reviews for a place")
    public ResponseEntity getPlaceReviews(
            @PathVariable UUID placeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        List<UserReviewResponse> reviews = reviewService.getPlaceReviews(placeId, userId, page, size);
        return ResponseEntity.ok(ofSucceeded(reviews));
    }

    @GetMapping("/users/me")
    @Operation(summary = "Get current user's reviews")
    public ResponseEntity getMyReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute("userId") UUID userId) {
        List<UserReviewResponse> reviews = reviewService.getUserReviews(userId, page, size);
        return ResponseEntity.ok(ofSucceeded(reviews));
    }

    @GetMapping("/feed")
    @Operation(summary = "Get reviews for feed, excluding current user's reviews when authenticated")
    public ResponseEntity getFeedReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        List<UserReviewResponse> reviews = reviewService.getFeedReviews(userId, page, size);
        return ResponseEntity.ok(ofSucceeded(reviews));
    }

    @PostMapping("/{id}/helpful")
    @Operation(summary = "Vote review as helpful (toggle)")
    public ResponseEntity voteHelpful(
            @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId) {
        UserReviewResponse response = reviewService.voteHelpful(userId, id);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping("/{id}/unhelpful")
    @Operation(summary = "Vote review as unhelpful (toggle)")
    public ResponseEntity voteUnhelpful(
            @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId) {
        UserReviewResponse response = reviewService.voteUnhelpful(userId, id);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @GetMapping("/places/{placeId}/score")
    @Operation(summary = "Get aggregated score for a place")
    public ResponseEntity getPlaceScore(@PathVariable UUID placeId) {
        PlaceScoreResponse response = reviewService.getPlaceScore(placeId);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @GetMapping("/users/me/profile")
    @Operation(summary = "Get current user's review profile (tier & stats)")
    public ResponseEntity getMyProfile(@RequestAttribute("userId") UUID userId) {
        UserReviewProfileResponse response = reviewService.getUserProfile(userId);
        return ResponseEntity.ok(ofSucceeded(response));
    }
}
