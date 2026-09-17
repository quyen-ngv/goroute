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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Duration;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/v1/api/admin/places")
@RequiredArgsConstructor
public class AdminPlaceController extends BaseController {

    private final AdminPlaceReviewRefreshService adminPlaceReviewRefreshService;
    private final PlaceService placeService;
    private final FoodService foodService;

    /**
     * The catalog is a compile-time constant (~42KB of JSON), so it is served here once
     * and revalidated by ETag rather than repeated inside every place response.
     */
    @GetMapping("/attribute-schema")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','get')")
    public ResponseEntity attributeSchema(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        String etag = "\"" + PlaceAttributeCatalog.version() + "\"";
        CacheControl cacheControl = CacheControl.maxAge(Duration.ofDays(1)).cachePrivate();
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cacheControl).build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(cacheControl)
                .body(ofSucceeded(PlaceAttributeCatalog.definitions()));
    }

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'places','get')")
    public ResponseEntity listPlaces(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) java.util.List<String> placeGroups,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ofSucceeded(placeService.getAdminPlaces(search, placeGroups, page, size)));
    }

    @GetMapping("/{placeId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','get')")
    public ResponseEntity getPlace(@PathVariable UUID placeId) {
        return ResponseEntity.ok(ofSucceeded(placeService.getAdminPlaceById(placeId)));
    }

    @PutMapping("/{placeId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    public ResponseEntity updatePlace(@PathVariable UUID placeId, @Valid @RequestBody UpdatePlaceRequest request) {
        placeService.updatePlace(placeId, request);
        return ResponseEntity.ok(ofSucceeded(placeService.getAdminPlaceById(placeId)));
    }

    @PostMapping("/import")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','create')")
    public ResponseEntity importPlace(@Valid @RequestBody ImportPlaceRequest request) {
        var imported = placeService.importPlace(request);
        return ResponseEntity.ok(ofSucceeded(imported == null ? null : placeService.getAdminPlaceById(imported.getId())));
    }

    @PostMapping("/import/batch")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','create')")
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
    public ResponseEntity refreshReviews(
            @PathVariable UUID placeId,
            @Valid @RequestBody(required = false) TriggerPlaceReviewRefreshRequest request) {
        Integer maxReviews = request == null ? null : request.getMaxReviews();
        PlaceReviewRefreshResponse response = adminPlaceReviewRefreshService.trigger(placeId, maxReviews);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping("/refresh-reviews")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    public ResponseEntity refreshAllActiveReviews() {
        return ResponseEntity.ok(ofSucceeded(adminPlaceReviewRefreshService.triggerAllActive()));
    }

    @GetMapping("/refresh-reviews/{jobId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','get')")
    public ResponseEntity getReviewRefreshStatus(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ofSucceeded(adminPlaceReviewRefreshService.getStatus(jobId)));
    }

    @PostMapping("/refresh-reviews/{jobId}/cancel")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    public ResponseEntity cancelReviewRefresh(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ofSucceeded(adminPlaceReviewRefreshService.cancel(jobId)));
    }

    @PostMapping("/refresh-reviews/{jobId}/rerun/{mode}")
    @PreAuthorize("@adminAuthorization.can(authentication,'places','update')")
    public ResponseEntity rerunReviewRefresh(
            @PathVariable UUID jobId,
            @PathVariable PlaceReviewRefreshRerunMode mode) {
        return ResponseEntity.ok(ofSucceeded(adminPlaceReviewRefreshService.rerun(jobId, mode)));
    }
}
