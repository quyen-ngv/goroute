package com.ds.goroute.controller;

import com.ds.goroute.dto.response.FoodDetailResponse;
import com.ds.goroute.dto.response.FoodPlacePageResponse;
import com.ds.goroute.dto.response.FoodSummaryResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.FoodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/foods")
@RequiredArgsConstructor
@Tag(name = "Foods", description = "Food discovery (public)")
public class FoodController extends BaseService {

    private final FoodService foodService;

    @GetMapping
    @Operation(summary = "List foods by city")
    public ResponseEntity listByCity(
            @RequestParam String citySlug,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int page) {
        List<FoodSummaryResponse> items = foodService.listByCity(citySlug, limit, page);
        return ResponseEntity.ok(ofSucceeded(items));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Food detail for city")
    public ResponseEntity getDetail(
            @PathVariable UUID id,
            @RequestParam String citySlug) {
        FoodDetailResponse detail = foodService.getDetail(id, citySlug);
        return ResponseEntity.ok(ofSucceeded(detail));
    }

    @GetMapping("/{id}/places")
    @Operation(summary = "Places serving this food in city")
    public ResponseEntity listPlaces(
            @PathVariable UUID id,
            @RequestParam String citySlug,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        FoodPlacePageResponse pageResponse = foodService.listPlacesForFood(id, citySlug, lat, lng, page, size);
        return ResponseEntity.ok(ofSucceeded(pageResponse));
    }
}
