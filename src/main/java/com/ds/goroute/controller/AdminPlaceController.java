package com.ds.goroute.controller;

import com.ds.goroute.dto.request.TriggerPlaceReviewRefreshRequest;
import com.ds.goroute.dto.response.PlaceReviewRefreshResponse;
import com.ds.goroute.service.AdminPlaceReviewRefreshService;
import com.ds.goroute.service.BaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/places")
@RequiredArgsConstructor
@Tag(name = "Admin Places", description = "Administrative place maintenance APIs")
public class AdminPlaceController extends BaseService {

    private final AdminPlaceReviewRefreshService adminPlaceReviewRefreshService;

    @PostMapping("/{placeId}/refresh-reviews")
    @Operation(summary = "Refresh up to five reviews after a place is activated")
    public ResponseEntity refreshReviews(
            @PathVariable UUID placeId,
            @Valid @RequestBody(required = false) TriggerPlaceReviewRefreshRequest request) {
        int maxReviews = request == null || request.getMaxReviews() == null ? 5 : request.getMaxReviews();
        PlaceReviewRefreshResponse response = adminPlaceReviewRefreshService.trigger(placeId, maxReviews);
        return ResponseEntity.ok(ofSucceeded(response));
    }
}
