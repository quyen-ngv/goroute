package com.ds.goroute.controller;

import com.ds.goroute.dto.request.ImportPlaceRequest;
import com.ds.goroute.dto.request.UpdatePlaceRequest;
import com.ds.goroute.dto.response.PlaceResponse;
import com.ds.goroute.dto.response.PlaceReviewResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.PlaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/places")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Places", description = "Place management APIs")
public class PlaceController extends BaseService {
    
    private final PlaceService placeService;
    
    @PostMapping("/import")
    @Operation(summary = "Import place from Google Maps data")
    public ResponseEntity importPlace(@Valid @RequestBody ImportPlaceRequest request) {
        PlaceResponse response = placeService.importPlace(request);
        return ResponseEntity.ok(ofSucceeded(response));
    }
    
    @PostMapping("/import/batch")
    @Operation(summary = "Import multiple places from Google Maps data")
    public ResponseEntity importPlaces(@Valid @RequestBody List<ImportPlaceRequest> requests) {
        List<PlaceResponse> responses = placeService.importPlaces(requests);
        return ResponseEntity.ok(ofSucceeded(responses));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get place by ID")
    public ResponseEntity getPlaceById(@PathVariable UUID id) {
        PlaceResponse response = placeService.getPlaceById(id);
        return ResponseEntity.ok(ofSucceeded(response));
    }
    
    @GetMapping("/google/{placeId}")
    @Operation(summary = "Get place by Google Place ID")
    public ResponseEntity getPlaceByGoogleId(@PathVariable String placeId) {
        PlaceResponse response = placeService.getPlaceByGoogleId(placeId);
        return ResponseEntity.ok(ofSucceeded(response));
    }
    
    @GetMapping("/search")
    @Operation(summary = "Search places by location and filters")
    public ResponseEntity searchPlaces(
            @RequestParam(required = false) String keyword,
            @RequestParam BigDecimal latitude,
            @RequestParam BigDecimal longitude,
            @RequestParam(defaultValue = "0.1") BigDecimal radius,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<PlaceResponse> responses = placeService.searchPlaces(keyword, latitude, longitude, radius, category, minRating, page, size);
        return ResponseEntity.ok(ofSucceeded(responses));
    }
    
    @GetMapping("/{id}/reviews")
    @Operation(summary = "Get reviews for a place")
    public ResponseEntity getPlaceReviews(@PathVariable UUID id) {
        List<PlaceReviewResponse> reviews = placeService.getPlaceReviews(id);
        return ResponseEntity.ok(ofSucceeded(reviews));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a place")
    public ResponseEntity deletePlace(@PathVariable UUID id) {
        placeService.deletePlace(id);
        return ResponseEntity.ok(ofSucceeded(null));
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update place information")
    public ResponseEntity updatePlace(@PathVariable UUID id, @Valid @RequestBody UpdatePlaceRequest request) {
        PlaceResponse response = placeService.updatePlace(id, request);
        return ResponseEntity.ok(ofSucceeded(response));
    }
}
