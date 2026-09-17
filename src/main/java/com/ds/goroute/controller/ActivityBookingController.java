package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.config.filter.AcceptCurrencyFilter;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AddBookingToTripRequest;
import com.ds.goroute.dto.request.ImportActivityBookingRequest;
import com.ds.goroute.dto.request.UpdateActivityBookingRequest;
import com.ds.goroute.dto.response.ActivityBookingResponse;
import com.ds.goroute.dto.response.ActivityResponse;
import com.ds.goroute.service.ActivityBookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/activity-bookings")
@RequiredArgsConstructor
@Slf4j
public class ActivityBookingController extends BaseController {

    private final ActivityBookingService activityBookingService;

    @PostMapping("/import")
    @PreAuthorize("@adminAuthorization.can(authentication,'activity-bookings','create')")
    public ResponseEntity<BaseResponse<ActivityBookingResponse>> importFromKlook(
            @Valid @RequestBody ImportActivityBookingRequest request) {
        ActivityBookingResponse response = activityBookingService.importFromKlook(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ofSucceeded(response));
    }

    @GetMapping("/search")
    public ResponseEntity<BaseResponse<List<ActivityBookingResponse>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) List<String> destinations,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(defaultValue = "50") double radiusKm,
            @RequestParam(required = false) Float minLuceneScore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String currency = AcceptCurrencyFilter.current();
        List<ActivityBookingResponse> responses = activityBookingService.search(
                q, minPrice, maxPrice, minRating, destinations,
                latitude, longitude, radiusKm, minLuceneScore, currency, page, size);
        return ResponseEntity.ok(ofSucceeded(responses));
    }

    @GetMapping("/search/by-place")
    public ResponseEntity<BaseResponse<List<ActivityBookingResponse>>> searchByPlace(
            @RequestParam UUID placeId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "50") double radiusKm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String currency = AcceptCurrencyFilter.current();
        List<ActivityBookingResponse> responses = activityBookingService.searchByPlace(
                placeId, q, radiusKm, currency, page, size);
        return ResponseEntity.ok(ofSucceeded(responses));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<ActivityBookingResponse>> getById(@PathVariable UUID id) {
        String currency = AcceptCurrencyFilter.current();
        ActivityBookingResponse response = activityBookingService.getById(id, currency);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'activity-bookings','update')")
    public ResponseEntity<BaseResponse<ActivityBookingResponse>> updateById(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateActivityBookingRequest request) {
        ActivityBookingResponse response = activityBookingService.updateById(id, request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'activity-bookings','delete')")
    public ResponseEntity<BaseResponse<String>> deleteById(@PathVariable UUID id) {
        activityBookingService.deleteById(id);
        return ResponseEntity.ok(ofSucceeded("Deleted successfully"));
    }

    @PostMapping("/{id}/add-to-trip")
    public ResponseEntity<BaseResponse<ActivityResponse>> addToTrip(
            @PathVariable UUID id,
            @Valid @RequestBody AddBookingToTripRequest request,
            @CurrentUser UUID userId) {
        String currency = (request.getTargetCurrency() != null && !request.getTargetCurrency().isBlank())
                ? request.getTargetCurrency()
                : AcceptCurrencyFilter.current();
        ActivityResponse response = activityBookingService.addToTrip(id, request, currency, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ofSucceeded(response));
    }

    @PostMapping("/indexing/trigger")
    @PreAuthorize("@adminAuthorization.can(authentication,'activity-bookings','update')")
    public ResponseEntity<BaseResponse<String>> triggerReindex() {
        activityBookingService.triggerReindex();
        return ResponseEntity.ok(ofSucceeded("Reindexing completed"));
    }
}
