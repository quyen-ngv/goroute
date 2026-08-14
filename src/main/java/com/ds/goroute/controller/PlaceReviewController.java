package com.ds.goroute.controller;

import com.ds.goroute.dto.request.BatchReviewRequest;
import com.ds.goroute.dto.request.RefreshPlaceReviewsRequest;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.PlaceReviewScoringService;
import com.ds.goroute.service.PlaceReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Place Reviews", description = "Place review management and scoring APIs")
public class PlaceReviewController extends BaseService {

    private final PlaceReviewService placeReviewService;
    private final PlaceReviewScoringService scoringService;

    @PostMapping("/batch")
    @Operation(summary = "Batch insert reviews from crawler data")
    public ResponseEntity batchInsertReviews(@Valid @RequestBody BatchReviewRequest request) {
        Map<String, Object> result = placeReviewService.batchInsertReviews(request.getReviews());
        return ResponseEntity.ok(ofSucceeded(result));
    }

    @PostMapping("/{placeId}/prepare-refresh")
    @Operation(summary = "Validate an ACTIVE place before a legacy review refresh")
    public ResponseEntity prepareRefresh(@PathVariable UUID placeId) {
        return ResponseEntity.ok(ofSucceeded(placeReviewService.prepareRefresh(placeId)));
    }

    @GetMapping("/refresh-candidates")
    @Operation(summary = "List review refresh candidates ordered by score/image migration need")
    public ResponseEntity refreshCandidates(
            @RequestParam(required = false) UUID placeId,
            @RequestParam(defaultValue = "24") @Min(1) @Max(8760) int maxAgeHours,
            @RequestParam(defaultValue = "false") boolean includeRecent) {
        return ResponseEntity.ok(ofSucceeded(
                placeReviewService.getRefreshCandidates(placeId, maxAgeHours, includeRecent)));
    }

    @PostMapping("/complete-refresh")
    @Operation(summary = "Score 200 image reviews and store up to 30 highest-legitimacy reviews")
    public ResponseEntity completeRefresh(@Valid @RequestBody RefreshPlaceReviewsRequest request) {
        return ResponseEntity.ok(ofSucceeded(placeReviewService.completeRefresh(request)));
    }

    @PostMapping("/calculate-scores")
    @Operation(summary = "Calculate authenticity scores for reviews and place overall scores")
    public ResponseEntity calculateScores(
            @RequestParam(required = false) String googlePlaceId,
            @RequestParam(defaultValue = "false") boolean forceRecalculate
    ) {
        Map<String, Integer> result = scoringService.runFullScoringJob(googlePlaceId, forceRecalculate);
        return ResponseEntity.ok(ofSucceeded(result));
    }

    @PostMapping("/calculate-review-scores")
    @Operation(summary = "Calculate authenticity scores for reviews only (Step 1)")
    public ResponseEntity calculateReviewScores(
            @RequestParam(required = false) String googlePlaceId,
            @RequestParam(defaultValue = "false") boolean forceRecalculate
    ) {
        int updated = scoringService.calculateReviewAuthenticityScores(googlePlaceId, forceRecalculate);
        return ResponseEntity.ok(ofSucceeded(Map.of("reviewsUpdated", updated)));
    }

    @PostMapping("/calculate-place-scores")
    @Operation(summary = "Calculate place overall scores only (Step 2)")
    public ResponseEntity calculatePlaceScores(
            @RequestParam(required = false) String googlePlaceId,
            @RequestParam(defaultValue = "false") boolean forceRecalculate
    ) {
        int updated = scoringService.recalculatePlaceScores(googlePlaceId, forceRecalculate);
        return ResponseEntity.ok(ofSucceeded(Map.of("placesUpdated", updated)));
    }
}
