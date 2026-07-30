package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.UpsertAppConfigRequest;
import com.ds.goroute.dto.response.AppConfigResponse;
import com.ds.goroute.service.AppConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/configs")
@RequiredArgsConstructor
public class AdminAppConfigController {
    private final AppConfigService service;

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'configs','get')")
    public ResponseEntity<BaseResponse<List<AppConfigResponse>>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String label,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminList(q, label, active, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'configs','get')")
    public ResponseEntity<BaseResponse<AppConfigResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminGet(id)));
    }

    @PostMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'configs','create')")
    public ResponseEntity<BaseResponse<AppConfigResponse>> create(@Valid @RequestBody UpsertAppConfigRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminCreate(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'configs','update')")
    public ResponseEntity<BaseResponse<AppConfigResponse>> update(@PathVariable UUID id,
            @Valid @RequestBody UpsertAppConfigRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminUpdate(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'configs','delete')")
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable UUID id) {
        service.adminDelete(id);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(null));
    }
}
