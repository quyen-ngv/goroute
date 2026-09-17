package com.ds.goroute.controller;

import com.ds.goroute.dto.request.BatchUpdatePlaceImagesRequest;
import com.ds.goroute.dto.PlaceSearchCriteria;
import com.ds.goroute.dto.request.ImportPlaceRequest;
import com.ds.goroute.dto.request.UpdatePlaceRequest;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.PlaceResponse;
import com.ds.goroute.dto.response.PlaceReviewResponse;
import com.ds.goroute.dto.response.FoodSummaryResponse;
import com.ds.goroute.dto.response.FoodTagResponse;
import com.ds.goroute.service.FoodService;
import com.ds.goroute.service.PlaceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/places")
@RequiredArgsConstructor
@Slf4j
public class PlaceController extends BaseController {

    private final PlaceService placeService;
    private final FoodService foodService;

    @PostMapping("/import")
    public ResponseEntity importPlace(@Valid @RequestBody ImportPlaceRequest request) {
        PlaceResponse response = placeService.importPlace(request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping("/import/batch")
    public ResponseEntity importPlaces(@Valid @RequestBody List<ImportPlaceRequest> requests) {
        List<PlaceResponse> responses = placeService.importPlaces(requests);
        return ResponseEntity.ok(ofSucceeded(responses));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<List<PlaceResponse>>> getAllPlaces(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<String> placeGroups,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int size) {
        List<PlaceResponse> responses = placeService.getAllPlaces(search, placeGroups, page, size);
        return ResponseEntity.ok(ofSucceeded(responses));
    }

    @GetMapping("/detail-refresh-candidates")
    public ResponseEntity<BaseResponse<Map<String, Object>>> detailRefreshCandidates(
            @RequestParam(required = false) UUID placeId,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) @Min(1) @Max(10000) Integer maxPlaces) {
        return ResponseEntity.ok(ofSucceeded(
                placeService.getDetailRefreshCandidates(placeId, includeInactive, maxPlaces)));
    }

    @GetMapping("/{id}")
    public ResponseEntity getPlaceById(@PathVariable UUID id) {
        PlaceResponse response = placeService.getPlaceById(id);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @GetMapping("/google/{placeId}")
    public ResponseEntity getPlaceByGoogleId(@PathVariable String placeId) {
        PlaceResponse response = placeService.getPlaceByGoogleId(placeId);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @GetMapping("/search")
    public ResponseEntity searchPlaces(
            @RequestParam(required = false) String keyword,
            @RequestParam @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @RequestParam(defaultValue = "0.1") @DecimalMin(value = "0", inclusive = false) BigDecimal radius,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> placeGroups,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(defaultValue = "false") boolean sortByRating,
            @RequestParam(required = false) String citySlug,
            @RequestParam(required = false) List<UUID> foodIds,
            @RequestParam(required = false) Boolean excludeLinkedFoodPlaces,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) Float minLuceneScore,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PlaceSearchCriteria.MAX_PAGE_SIZE) int size
    ) {
        List<PlaceResponse> responses = placeService.searchPlaces(
                keyword, latitude, longitude, radius, category, placeGroups, minRating, sortByRating,
                citySlug, foodIds, excludeLinkedFoodPlaces, includeInactive, minLuceneScore, page, size);
        return ResponseEntity.ok(ofSucceeded(responses));
    }

    @GetMapping("/{id}/reviews")
    public ResponseEntity getPlaceReviews(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<PlaceReviewResponse> reviews = placeService.getPlaceReviews(id, page, size);
        return ResponseEntity.ok(ofSucceeded(reviews));
    }

    @GetMapping("/{id}/foods")
    public ResponseEntity listFoodsForPlace(
            @PathVariable UUID id,
            @RequestParam String citySlug) {
        List<FoodSummaryResponse> items = foodService.listFoodsForPlace(id, citySlug);
        return ResponseEntity.ok(ofSucceeded(items));
    }

    @GetMapping("/{id}/food-tags")
    public ResponseEntity listFoodTagsForPlace(@PathVariable UUID id) {
        List<FoodTagResponse> items = foodService.adminListFoodTagsForPlace(id);
        return ResponseEntity.ok(ofSucceeded(items));
    }

    @PostMapping("/{id}/food-tags/{foodId}")
    public ResponseEntity linkFoodToPlace(@PathVariable UUID id, @PathVariable UUID foodId) {
        foodService.adminLinkFoodToPlace(id, foodId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @DeleteMapping("/{id}/food-tags/{foodId}")
    public ResponseEntity unlinkFoodFromPlace(@PathVariable UUID id, @PathVariable UUID foodId) {
        foodService.adminUnlinkFoodFromPlace(id, foodId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/indexing/trigger")
    public ResponseEntity triggerSearchReindex() {
        placeService.triggerSearchReindex();
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity deletePlace(@PathVariable UUID id) {
        placeService.deletePlace(id);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PutMapping("/{id}")
    public ResponseEntity updatePlace(@PathVariable UUID id, @Valid @RequestBody UpdatePlaceRequest request) {
        PlaceResponse response = placeService.updatePlace(id, request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping("/batch-update-images")
    public ResponseEntity batchUpdatePlaceImages(@Valid @RequestBody BatchUpdatePlaceImagesRequest request) {
        Map<String, Object> result = placeService.batchUpdatePlaceImages(request);
        return ResponseEntity.ok(ofSucceeded(result));
    }
}
