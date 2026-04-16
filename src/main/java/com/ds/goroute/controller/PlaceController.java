package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.DistanceMatrixRequest;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.service.GoogleMapsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/api/places")
@RequiredArgsConstructor
@Slf4j
public class PlaceController {

    private final GoogleMapsService googleMapsService;

    @GetMapping("/search")
    public ResponseEntity<BaseResponse<List<PlaceSearchResponse>>> searchPlaces(
            @RequestParam String query,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        log.info("Searching places: query={}, lat={}, lng={}", query, lat, lng);
        List<PlaceSearchResponse> results = googleMapsService.searchPlaces(query, lat, lng);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(results));
    }

    @GetMapping("/{placeId}")
    public ResponseEntity<BaseResponse<PlaceDetailResponse>> getPlaceDetails(
            @PathVariable String placeId) {
        log.info("Getting place details: placeId={}", placeId);
        PlaceDetailResponse details = googleMapsService.getPlaceDetails(placeId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(details));
    }

    @GetMapping("/nearby")
    public ResponseEntity<BaseResponse<List<NearbyPlaceResponse>>> nearbySearch(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "5000") Integer radius,
            @RequestParam(defaultValue = "restaurant") String type) {
        log.info("Nearby search: lat={}, lng={}, radius={}, type={}", lat, lng, radius, type);
        List<NearbyPlaceResponse> results = googleMapsService.nearbySearch(lat, lng, radius, type);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(results));
    }

    @GetMapping("/route")
    public ResponseEntity<BaseResponse<RouteResponse>> calculateRoute(
            @RequestParam Double originLat,
            @RequestParam Double originLng,
            @RequestParam Double destLat,
            @RequestParam Double destLng,
            @RequestParam(defaultValue = "driving") String travelMode) {
        log.info("Calculating route: origin=({},{}), dest=({},{}), mode={}", 
                originLat, originLng, destLat, destLng, travelMode);
        RouteResponse route = googleMapsService.calculateRoute(originLat, originLng, destLat, destLng, travelMode);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(route));
    }

    @PostMapping("/distance-matrix")
    public ResponseEntity<BaseResponse<DistanceMatrixResponse>> calculateDistanceMatrix(
            @RequestBody DistanceMatrixRequest request) {
        log.info("Calculating distance matrix: origins={}, destinations={}, mode={}", 
                request.getOrigins().size(), request.getDestinations().size(), request.getTravelMode());
        DistanceMatrixResponse matrix = googleMapsService.calculateDistanceMatrix(
                request.getOrigins(), 
                request.getDestinations(), 
                request.getTravelMode());
        return ResponseEntity.ok(BaseResponse.ofSucceeded(matrix));
    }
}
