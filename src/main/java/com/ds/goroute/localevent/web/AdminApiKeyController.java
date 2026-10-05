package com.ds.goroute.localevent.web;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.localevent.dto.AdminApiKeyCreateRequest;
import com.ds.goroute.localevent.dto.AdminApiKeyCreatedResponse;
import com.ds.goroute.localevent.dto.AdminApiKeyResponse;
import com.ds.goroute.localevent.service.AdminApiKeyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

import java.util.UUID;

/**
 * Admin API keys (header {@code X-Admin-Api-Key}) for machine callers such as the events crawler.
 * The key itself is returned once, by create. Gated on {@code api-keys:manage}.
 */
@Validated
@RestController
@RequestMapping("/v1/api/admin/api-keys")
@RequiredArgsConstructor
public class AdminApiKeyController {

    private final AdminApiKeyService apiKeyService;

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'api-keys','manage')")
    public ResponseEntity<BaseResponse<PageResponse<AdminApiKeyResponse>>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(apiKeyService.list(page, size)));
    }

    @PostMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'api-keys','manage')")
    public ResponseEntity<BaseResponse<AdminApiKeyCreatedResponse>> create(
            @CurrentUser UUID userId,
            @Valid @RequestBody AdminApiKeyCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.ofSucceeded(apiKeyService.create(request, userId)));
    }

    @PostMapping("/{keyId}/revoke")
    @PreAuthorize("@adminAuthorization.can(authentication,'api-keys','manage')")
    public ResponseEntity<BaseResponse<AdminApiKeyResponse>> revoke(@PathVariable UUID keyId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(apiKeyService.revoke(keyId)));
    }
}
