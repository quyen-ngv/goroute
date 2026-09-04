package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.UpdateUserQuotaOverridesRequest;
import com.ds.goroute.dto.response.AdminUserQuotaResponse;
import com.ds.goroute.service.AdminUserQuotaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/users/{userId}/quotas")
@RequiredArgsConstructor
public class AdminUserQuotaController extends BaseController {
    private final AdminUserQuotaService service;

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'users','get')")
    public ResponseEntity<BaseResponse<AdminUserQuotaResponse>> get(@PathVariable UUID userId) {
        return ResponseEntity.ok(ofSucceeded(service.get(userId)));
    }

    @PutMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'users','update')")
    public ResponseEntity<BaseResponse<AdminUserQuotaResponse>> update(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserQuotaOverridesRequest request) {
        return ResponseEntity.ok(ofSucceeded(service.update(userId, request)));
    }
}
