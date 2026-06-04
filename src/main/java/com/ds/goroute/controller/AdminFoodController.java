package com.ds.goroute.controller;

import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.FoodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/foods")
@RequiredArgsConstructor
@Tag(name = "Admin Foods", description = "Food CMS")
public class AdminFoodController extends BaseService {

    private final FoodService foodService;

    @GetMapping("/city-slugs")
    public ResponseEntity listCitySlugs() {
        return ResponseEntity.ok(ofSucceeded(foodService.listCitySlugOptions()));
    }

    @GetMapping
    public ResponseEntity listAll(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ofSucceeded(foodService.adminListAll(q, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity getDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(ofSucceeded(foodService.adminGetDetail(id)));
    }

    @PostMapping
    public ResponseEntity create(@Valid @RequestBody CreateFoodRequest request) {
        AdminFoodDetailResponse created = foodService.adminCreate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ofSucceeded(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity update(@PathVariable UUID id, @Valid @RequestBody UpdateFoodRequest request) {
        return ResponseEntity.ok(ofSucceeded(foodService.adminUpdate(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity delete(@PathVariable UUID id) {
        foodService.adminDelete(id);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @GetMapping("/{foodId}/city-scores")
    public ResponseEntity listCityScores(@PathVariable UUID foodId) {
        return ResponseEntity.ok(ofSucceeded(foodService.adminListCityScores(foodId)));
    }

    @PostMapping("/{foodId}/city-scores")
    public ResponseEntity createCityScore(
            @PathVariable UUID foodId,
            @Valid @RequestBody CreateFoodCityScoreRequest request) {
        FoodCityScoreResponse score = foodService.adminCreateCityScore(foodId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ofSucceeded(score));
    }

    @PutMapping("/{foodId}/city-scores/{scoreId}")
    public ResponseEntity updateCityScore(
            @PathVariable UUID foodId,
            @PathVariable UUID scoreId,
            @Valid @RequestBody UpdateFoodCityScoreRequest request) {
        return ResponseEntity.ok(ofSucceeded(foodService.adminUpdateCityScore(foodId, scoreId, request)));
    }

    @DeleteMapping("/{foodId}/city-scores/{scoreId}")
    public ResponseEntity deleteCityScore(@PathVariable UUID foodId, @PathVariable UUID scoreId) {
        foodService.adminDeleteCityScore(foodId, scoreId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @GetMapping("/{foodId}/linked-places")
    public ResponseEntity listLinkedPlaces(
            @PathVariable UUID foodId,
            @RequestParam(required = false) String citySlug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ofSucceeded(foodService.adminListLinkedPlaces(foodId, citySlug, page, size)));
    }

    @PostMapping("/{foodId}/linked-places")
    public ResponseEntity linkPlace(
            @PathVariable UUID foodId,
            @Valid @RequestBody LinkFoodPlaceRequest request) {
        foodService.adminLinkPlace(foodId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ofSucceeded(null));
    }

    @PostMapping("/{foodId}/linked-places/batch")
    public ResponseEntity batchLink(
            @PathVariable UUID foodId,
            @Valid @RequestBody BatchLinkFoodPlacesRequest request) {
        foodService.adminBatchLinkPlaces(foodId, request);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @DeleteMapping("/{foodId}/linked-places/{placeId}")
    public ResponseEntity unlinkPlace(@PathVariable UUID foodId, @PathVariable UUID placeId) {
        foodService.adminUnlinkPlace(foodId, placeId);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
