package com.ds.goroute.controller;

import com.ds.goroute.dto.request.TriggerPlaceReviewRefreshRequest;
import com.ds.goroute.dto.request.ImportPlaceRequest;
import com.ds.goroute.dto.request.UpdatePlaceRequest;
import com.ds.goroute.dto.response.AdminPlaceResponse;
import com.ds.goroute.dto.response.PlaceReviewRefreshResponse;
import com.ds.goroute.service.AdminPlaceReviewRefreshService;
import com.ds.goroute.service.FoodService;
import com.ds.goroute.service.PlaceAttributeCatalog;
import com.ds.goroute.service.PlaceService;
import com.ds.goroute.type.PlaceReviewRefreshRerunMode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/v1/api/admin/places")
@RequiredArgsConstructor
@Tag(name = "Admin Places", description = "Administrative place maintenance APIs")
public class AdminPlaceController extends BaseController {

    private final AdminPlaceReviewRefreshService adminPlaceReviewRefreshService;
    private final PlaceService placeService;
    private final FoodService foodService;

    @GetMapping("/attribute-schema")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','get')")
    @Operation(summary = "Get the complete schema v1 place attribute catalog")
    public ResponseEntity attributeSchema() {
        return ResponseEntity.ok(ofSucceeded(PlaceAttributeCatalog.definitions()));
    }

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'places','get')")
    @Operation(summary = "List places with complete admin attributes")
    public ResponseEntity listPlaces(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ofSucceeded(placeService.getAdminPlaces(search, page, size)));
    }

    @GetMapping("/{placeId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','get')")
    @Operation(summary = "Get a place with complete admin attributes")
    public ResponseEntity getPlace(@PathVariable UUID placeId) {
        return ResponseEntity.ok(ofSucceeded(placeService.getAdminPlaceById(placeId)));
    }

    @PutMapping("/{placeId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    @Operation(summary = "Update a place from the admin console")
    public ResponseEntity updatePlace(@PathVariable UUID placeId, @Valid @RequestBody UpdatePlaceRequest request) {
        placeService.updatePlace(placeId, request);
        return ResponseEntity.ok(ofSucceeded(placeService.getAdminPlaceById(placeId)));
    }

    @PostMapping("/import")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','create')")
    @Operation(summary = "Import one place from the admin console")
    public ResponseEntity importPlace(@Valid @RequestBody ImportPlaceRequest request) {
        var imported = placeService.importPlace(request);
        return ResponseEntity.ok(ofSucceeded(imported == null ? null : placeService.getAdminPlaceById(imported.getId())));
    }

    @PostMapping("/import/batch")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','create')")
    @Operation(summary = "Import multiple places from the admin console")
    public ResponseEntity importPlaces(@Valid @RequestBody List<ImportPlaceRequest> requests) {
        var imported = placeService.importPlaces(requests);
        List<AdminPlaceResponse> result = imported.stream()
                .filter(item -> item != null && item.getId() != null)
                .map(item -> placeService.getAdminPlaceById(item.getId()))
                .toList();
        return ResponseEntity.ok(ofSucceeded(result));
    }

    @DeleteMapping("/{placeId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','delete')")
    @Operation(summary = "Delete a place from the admin console")
    public ResponseEntity deletePlace(@PathVariable UUID placeId) {
        placeService.deletePlace(placeId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/indexing/trigger")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    public ResponseEntity triggerSearchReindex() {
        placeService.triggerSearchReindex();
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @GetMapping("/{placeId}/food-tags")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','get')")
    public ResponseEntity listFoodTags(@PathVariable UUID placeId) {
        return ResponseEntity.ok(ofSucceeded(foodService.adminListFoodTagsForPlace(placeId)));
    }

    @PostMapping("/{placeId}/food-tags/{foodId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    public ResponseEntity linkFood(@PathVariable UUID placeId, @PathVariable UUID foodId) {
        foodService.adminLinkFoodToPlace(placeId, foodId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @DeleteMapping("/{placeId}/food-tags/{foodId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    public ResponseEntity unlinkFood(@PathVariable UUID placeId, @PathVariable UUID foodId) {
        foodService.adminUnlinkFoodFromPlace(placeId, foodId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/{placeId}/refresh-reviews")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    @Operation(summary = "Queue a one-place review refresh")
    public ResponseEntity refreshReviews(
            @PathVariable UUID placeId,
            @Valid @RequestBody(required = false) TriggerPlaceReviewRefreshRequest request) {
        Integer maxReviews = request == null ? null : request.getMaxReviews();
        PlaceReviewRefreshResponse response = adminPlaceReviewRefreshService.trigger(placeId, maxReviews);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping("/refresh-reviews")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    @Operation(summary = "Queue a sequential review refresh for every eligible ACTIVE place")
    public ResponseEntity refreshAllActiveReviews() {
        return ResponseEntity.ok(ofSucceeded(adminPlaceReviewRefreshService.triggerAllActive()));
    }

    @GetMapping("/refresh-reviews/{jobId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','get')")
    @Operation(summary = "Get review refresh progress and per-place results")
    public ResponseEntity getReviewRefreshStatus(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ofSucceeded(adminPlaceReviewRefreshService.getStatus(jobId)));
    }

    @PostMapping("/refresh-reviews/{jobId}/cancel")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    @Operation(summary = "Stop a running review refresh job after its current place")
    public ResponseEntity cancelReviewRefresh(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ofSucceeded(adminPlaceReviewRefreshService.cancel(jobId)));
    }

    @PostMapping("/refresh-reviews/{jobId}/rerun/{mode}")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    @Operation(summary = "Rerun all, failed, unexecuted, or failed and unexecuted places")
    public ResponseEntity rerunReviewRefresh(
            @PathVariable UUID jobId,
            @PathVariable PlaceReviewRefreshRerunMode mode) {
        return ResponseEntity.ok(ofSucceeded(adminPlaceReviewRefreshService.rerun(jobId, mode)));
    }
}
