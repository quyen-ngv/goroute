package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AssignCheckinPlaceRequest;
import com.ds.goroute.dto.request.HideCheckinRequest;
import com.ds.goroute.dto.response.AdminCheckinResponse;
import com.ds.goroute.dto.response.CheckinLocationHistoryResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.service.CheckinAdminService;
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

/** Lets an admin browse individual check-ins and hide/show one directly (epic 06). */
@RestController
@RequestMapping("/v1/api/admin/checkins")
@RequiredArgsConstructor
@Validated
public class AdminUserCheckinController extends BaseController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CheckinAdminService service;

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'checkins','get')")
    public ResponseEntity<BaseResponse<PageResponse<AdminCheckinResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Boolean hidden,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        List<AdminCheckinResponse> items = service.list(search, userId, hidden, page, size);
        long total = service.count(search, userId, hidden);
        return ResponseEntity.ok(ofSucceeded(PageResponse.of(items, total, page, size)));
    }

    @PostMapping("/{checkinId}/hide")
    @PreAuthorize("@adminAuthorization.can(authentication,'checkins','update')")
    public ResponseEntity<BaseResponse<Void>> hide(
            @PathVariable UUID checkinId,
            @Valid @RequestBody HideCheckinRequest request,
            @CurrentUser UUID adminId) {
        service.hide(adminId, checkinId, request);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/{checkinId}/show")
    @PreAuthorize("@adminAuthorization.can(authentication,'checkins','update')")
    public ResponseEntity<BaseResponse<Void>> show(
            @PathVariable UUID checkinId,
            @CurrentUser UUID adminId) {
        service.show(adminId, checkinId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    /** Links the check-in to the catalogue place it actually happened at. */
    @PostMapping("/{checkinId}/place")
    @PreAuthorize("@adminAuthorization.can(authentication,'checkins','update')")
    public ResponseEntity<BaseResponse<Void>> assignPlace(
            @PathVariable UUID checkinId,
            @Valid @RequestBody AssignCheckinPlaceRequest request,
            @CurrentUser UUID adminId) {
        service.assignPlace(adminId, checkinId, request);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @GetMapping("/{checkinId}/location-history")
    @PreAuthorize("@adminAuthorization.can(authentication,'checkins','get')")
    public ResponseEntity<BaseResponse<List<CheckinLocationHistoryResponse>>> locationHistory(
            @PathVariable UUID checkinId) {
        return ResponseEntity.ok(ofSucceeded(service.locationHistory(checkinId)));
    }
}
