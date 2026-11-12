package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.TripModerationRequest;
import com.ds.goroute.dto.response.AdminPublicTripResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.service.TripAdminService;
import com.ds.goroute.type.TripVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Lets an admin browse shared trips and hide/show or unpublish one directly (epic 02, epic 10). */
@RestController
@RequestMapping("/v1/api/admin/public-trips")
@RequiredArgsConstructor
@Validated
public class AdminPublicTripController extends BaseController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TripAdminService service;

    /** @param visibility PUBLIC or SHARED; omitted for both */
    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'public-trips','get')")
    public ResponseEntity<BaseResponse<PageResponse<AdminPublicTripResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) TripVisibility visibility,
            @RequestParam(required = false) Boolean hidden,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        List<AdminPublicTripResponse> items = service.list(search, ownerId, visibility, hidden, page, size);
        long total = service.count(search, ownerId, visibility, hidden);
        return ResponseEntity.ok(ofSucceeded(PageResponse.of(items, total, page, size)));
    }

    @PostMapping("/{tripId}/hide")
    @PreAuthorize("@adminAuthorization.can(authentication,'public-trips','update')")
    public ResponseEntity<BaseResponse<Void>> hide(
            @PathVariable UUID tripId,
            @Valid @RequestBody TripModerationRequest request,
            @CurrentUser UUID adminId) {
        service.hide(adminId, tripId, request);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/{tripId}/show")
    @PreAuthorize("@adminAuthorization.can(authentication,'public-trips','update')")
    public ResponseEntity<BaseResponse<Void>> show(
            @PathVariable UUID tripId,
            @CurrentUser UUID adminId) {
        service.show(adminId, tripId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    /** "Remove from public": sets the trip to PRIVATE; the owner and members keep it. */
    @PostMapping("/{tripId}/unpublish")
    @PreAuthorize("@adminAuthorization.can(authentication,'public-trips','update')")
    public ResponseEntity<BaseResponse<Void>> unpublish(
            @PathVariable UUID tripId,
            @Valid @RequestBody TripModerationRequest request,
            @CurrentUser UUID adminId) {
        service.unpublish(adminId, tripId, request);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
