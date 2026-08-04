package com.ds.goroute.controller;

import com.ds.goroute.dto.request.TriggerPlaceReviewRefreshRequest;
import com.ds.goroute.dto.request.ImportPlaceRequest;
import com.ds.goroute.dto.request.UpdatePlaceRequest;
import com.ds.goroute.dto.response.AdminPlaceResponse;
import com.ds.goroute.dto.response.PlaceReviewRefreshResponse;
import com.ds.goroute.service.AdminPlaceReviewRefreshService;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.FoodService;
import com.ds.goroute.service.PlaceAttributeCatalog;
import com.ds.goroute.service.PlaceService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/v1/api/admin/places")
@RequiredArgsConstructor
@Tag(name = "Admin Places", description = "Administrative place maintenance APIs")
public class AdminPlaceController extends BaseService {

    private final AdminPlaceReviewRefreshService adminPlaceReviewRefreshService;
    private final PlaceService placeService;
    private final FoodService foodService;

    @GetMapping("/attribute-schema")
    @Operation(summary = "Get the complete schema v1 place attribute catalog")
    public ResponseEntity attributeSchema() {
        return ResponseEntity.ok(ofSucceeded(PlaceAttributeCatalog.definitions()));
    }

    @GetMapping
    @Operation(summary = "List places with complete admin attributes")
    public ResponseEntity listPlaces() {
        return ResponseEntity.ok(ofSucceeded(placeService.getAdminPlaces()));
    }

    @GetMapping("/{placeId}")
    @Operation(summary = "Get a place with complete admin attributes")
    public ResponseEntity getPlace(@PathVariable UUID placeId) {
        return ResponseEntity.ok(ofSucceeded(placeService.getAdminPlaceById(placeId)));
    }

    @PutMapping("/{placeId}")
    @Operation(summary = "Update a place from the admin console")
    public ResponseEntity updatePlace(@PathVariable UUID placeId, @Valid @RequestBody UpdatePlaceRequest request) {
        placeService.updatePlace(placeId, request);
        return ResponseEntity.ok(ofSucceeded(placeService.getAdminPlaceById(placeId)));
    }

    @PostMapping("/import")
    @Operation(summary = "Import one place from the admin console")
    public ResponseEntity importPlace(@Valid @RequestBody ImportPlaceRequest request) {
        var imported = placeService.importPlace(request);
        return ResponseEntity.ok(ofSucceeded(imported == null ? null : placeService.getAdminPlaceById(imported.getId())));
    }

    @PostMapping("/import/batch")
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
    @Operation(summary = "Delete a place from the admin console")
    public ResponseEntity deletePlace(@PathVariable UUID placeId) {
        placeService.deletePlace(placeId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/indexing/trigger")
    public ResponseEntity triggerSearchReindex() {
        placeService.triggerSearchReindex();
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @GetMapping("/{placeId}/food-tags")
    public ResponseEntity listFoodTags(@PathVariable UUID placeId) {
        return ResponseEntity.ok(ofSucceeded(foodService.adminListFoodTagsForPlace(placeId)));
    }

    @PostMapping("/{placeId}/food-tags/{foodId}")
    public ResponseEntity linkFood(@PathVariable UUID placeId, @PathVariable UUID foodId) {
        foodService.adminLinkFoodToPlace(placeId, foodId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @DeleteMapping("/{placeId}/food-tags/{foodId}")
    public ResponseEntity unlinkFood(@PathVariable UUID placeId, @PathVariable UUID foodId) {
        foodService.adminUnlinkFoodFromPlace(placeId, foodId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

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
