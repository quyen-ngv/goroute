package com.ds.goroute.controller;

import com.ds.goroute.dto.request.BatchReviewRequest;
import com.ds.goroute.dto.request.RefreshPlaceReviewsRequest;
import com.ds.goroute.service.PlaceReviewScoringService;
import com.ds.goroute.service.PlaceReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/place-reviews")
@RequiredArgsConstructor
@Slf4j
public class PlaceReviewController extends BaseController {

    private final PlaceReviewService placeReviewService;
    private final PlaceReviewScoringService scoringService;

    @PostMapping("/batch")
    public ResponseEntity batchInsertReviews(@Valid @RequestBody BatchReviewRequest request) {
        Map<String, Object> result = placeReviewService.batchInsertReviews(request.getReviews());
        return ResponseEntity.ok(ofSucceeded(result));
    }

    @PostMapping("/{placeId}/prepare-refresh")
    public ResponseEntity prepareRefresh(@PathVariable UUID placeId) {
        return ResponseEntity.ok(ofSucceeded(placeReviewService.prepareRefresh(placeId)));
    }

    @GetMapping("/refresh-settings")
    public ResponseEntity refreshSettings() {
        return ResponseEntity.ok(ofSucceeded(placeReviewService.getRefreshSettings()));
    }

    @GetMapping("/refresh-candidates")
    public ResponseEntity refreshCandidates(
            @RequestParam(required = false) UUID placeId,
            @RequestParam(defaultValue = "24") @Min(1) @Max(8760) int maxAgeHours,
            @RequestParam(defaultValue = "false") boolean includeRecent) {
        return ResponseEntity.ok(ofSucceeded(
                placeReviewService.getRefreshCandidates(placeId, maxAgeHours, includeRecent)));
    }

    @PostMapping("/complete-refresh")
    public ResponseEntity completeRefresh(@Valid @RequestBody RefreshPlaceReviewsRequest request) {
        return ResponseEntity.ok(ofSucceeded(placeReviewService.completeRefresh(request)));
    }

    @PostMapping("/calculate-scores")
    public ResponseEntity calculateScores(
            @RequestParam(required = false) String googlePlaceId,
            @RequestParam(defaultValue = "false") boolean forceRecalculate
    ) {
        Map<String, Integer> result = scoringService.runFullScoringJob(googlePlaceId, forceRecalculate);
        return ResponseEntity.ok(ofSucceeded(result));
    }

    @PostMapping("/calculate-review-scores")
    public ResponseEntity calculateReviewScores(
            @RequestParam(required = false) String googlePlaceId,
            @RequestParam(defaultValue = "false") boolean forceRecalculate
    ) {
        int updated = scoringService.calculateReviewAuthenticityScores(googlePlaceId, forceRecalculate);
        return ResponseEntity.ok(ofSucceeded(Map.of("reviewsUpdated", updated)));
    }

    @PostMapping("/calculate-place-scores")
    public ResponseEntity calculatePlaceScores(
            @RequestParam(required = false) String googlePlaceId,
            @RequestParam(defaultValue = "false") boolean forceRecalculate
    ) {
        int updated = scoringService.recalculatePlaceScores(googlePlaceId, forceRecalculate);
        return ResponseEntity.ok(ofSucceeded(Map.of("placesUpdated", updated)));
    }
}
