package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.DecideTrustRoleRequest;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.dto.response.TrustRoleResponse;
import com.ds.goroute.service.TrustRoleService;
import com.ds.goroute.type.TrustRole;
import com.ds.goroute.type.TrustRoleStatus;
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

/** The review queue for community roles (TRUST-02). */
@RestController
@RequestMapping("/v1/api/admin/trust-roles")
@RequiredArgsConstructor
@Validated
public class AdminTrustRoleController extends BaseController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TrustRoleService trustRoleService;

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'trust-roles','get')")
    public ResponseEntity<BaseResponse<PageResponse<TrustRoleResponse>>> queue(
            @RequestParam(defaultValue = "PENDING") TrustRoleStatus status,
            @RequestParam(required = false) TrustRole role,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        List<TrustRoleResponse> items = trustRoleService.queue(status, role, page, size);
        return ResponseEntity.ok(ofSucceeded(
                PageResponse.of(items, trustRoleService.countQueue(status, role), page, size)));
    }

    @PostMapping("/{roleId}/decide")
    @PreAuthorize("@adminAuthorization.can(authentication,'trust-roles','update')")
    public ResponseEntity<BaseResponse<TrustRoleResponse>> decide(
            @PathVariable UUID roleId,
            @Valid @RequestBody DecideTrustRoleRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(trustRoleService.decide(userId, roleId, request)));
    }

    /** Roles whose review date has passed and that should be looked at again. */
    @GetMapping("/due-for-review")
    @PreAuthorize("@adminAuthorization.can(authentication,'trust-roles','get')")
    public ResponseEntity<BaseResponse<List<TrustRoleResponse>>> dueForReview(
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int limit) {
        return ResponseEntity.ok(ofSucceeded(trustRoleService.dueForReview(limit)));
    }
}
