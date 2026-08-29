package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.PromoteCheckinClusterRequest;
import com.ds.goroute.dto.response.CheckinClusterResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.CheckinClusterAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Promoting places people actually go to into the catalogue (CHK-12). */
@RestController
@RequestMapping("/v1/api/admin/checkin-clusters")
@RequiredArgsConstructor
@Validated
public class AdminCheckinClusterController extends BaseService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CheckinClusterAdminService service;

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'checkin-clusters','get')")
    public ResponseEntity<BaseResponse<PageResponse<CheckinClusterResponse>>> queue(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        List<CheckinClusterResponse> items = service.queue(page, size);
        return ResponseEntity.ok(ofSucceeded(PageResponse.of(items, service.countQueue(), page, size)));
    }

    @GetMapping("/{locationKey}")
    @PreAuthorize("@adminAuthorization.can(authentication,'checkin-clusters','get')")
    public ResponseEntity<BaseResponse<CheckinClusterResponse>> get(
            @PathVariable @Size(max = 40) String locationKey) {
        return ResponseEntity.ok(ofSucceeded(service.get(locationKey)));
    }

    @PostMapping("/{locationKey}/promote")
    @PreAuthorize("@adminAuthorization.can(authentication,'checkin-clusters','update')")
    public ResponseEntity<BaseResponse<CheckinClusterResponse>> promote(
            @PathVariable @Size(max = 40) String locationKey,
            @Valid @RequestBody PromoteCheckinClusterRequest request,
            @RequestAttribute UUID userId) {
        return ResponseEntity.ok(ofSucceeded(service.promote(userId, locationKey, request)));
    }

    @PostMapping("/{locationKey}/merge")
    @PreAuthorize("@adminAuthorization.can(authentication,'checkin-clusters','update')")
    public ResponseEntity<BaseResponse<CheckinClusterResponse>> merge(
            @PathVariable @Size(max = 40) String locationKey,
            @RequestParam UUID placeId,
            @RequestAttribute UUID userId) {
        return ResponseEntity.ok(ofSucceeded(service.merge(userId, locationKey, placeId)));
    }

    @PostMapping("/{locationKey}/ignore")
    @PreAuthorize("@adminAuthorization.can(authentication,'checkin-clusters','update')")
    public ResponseEntity<BaseResponse<Void>> ignore(
            @PathVariable @Size(max = 40) String locationKey,
            @RequestParam(required = false) @Size(max = 1000) String note,
            @RequestAttribute UUID userId) {
        service.ignore(userId, locationKey, note);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/{locationKey}/revert")
    @PreAuthorize("@adminAuthorization.can(authentication,'checkin-clusters','update')")
    public ResponseEntity<BaseResponse<Void>> revert(
            @PathVariable @Size(max = 40) String locationKey,
            @RequestAttribute UUID userId) {
        service.revertPromotion(userId, locationKey);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
