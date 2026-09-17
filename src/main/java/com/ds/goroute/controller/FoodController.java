package com.ds.goroute.controller;

import com.ds.goroute.dto.response.CitySlugOptionResponse;
import com.ds.goroute.dto.response.FoodDetailResponse;
import com.ds.goroute.dto.response.FoodPlacePageResponse;
import com.ds.goroute.dto.response.FoodSummaryResponse;
import com.ds.goroute.service.FoodService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/foods")
@RequiredArgsConstructor
public class FoodController extends BaseController {

    private final FoodService foodService;

    @GetMapping
    public ResponseEntity listByCity(
            @RequestParam String citySlug,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int page) {
        List<FoodSummaryResponse> items = foodService.listByCity(citySlug, limit, page);
        return ResponseEntity.ok(ofSucceeded(items));
    }

    @GetMapping("/{id}")
    public ResponseEntity getDetail(
            @PathVariable UUID id,
            @RequestParam String citySlug) {
        FoodDetailResponse detail = foodService.getDetail(id, citySlug);
        return ResponseEntity.ok(ofSucceeded(detail));
    }

    @GetMapping("/{id}/cities")
    public ResponseEntity listCities(@PathVariable UUID id) {
        List<CitySlugOptionResponse> cities = foodService.listCitiesForFood(id);
        return ResponseEntity.ok(ofSucceeded(cities));
    }

    @GetMapping("/{id}/places")
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
