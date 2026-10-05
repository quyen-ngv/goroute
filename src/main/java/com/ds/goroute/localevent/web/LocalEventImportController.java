package com.ds.goroute.localevent.web;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.config.filter.AdminApiKeyAuthenticationFilter;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.localevent.dto.LocalEventImportBatchDetailResponse;
import com.ds.goroute.localevent.dto.LocalEventImportBatchResponse;
import com.ds.goroute.localevent.dto.LocalEventImportRequest;
import com.ds.goroute.localevent.dto.LocalEventImportResponse;
import com.ds.goroute.localevent.service.LocalEventImportBatchService;
import com.ds.goroute.localevent.service.LocalEventImportService;
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
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The events import contract for the external AI crawler (epic 17, documented in
 * .docs/designs/apis/25_EVENTS_IMPORT_API.md). Reachable with a console JWT or with
 * {@code X-Admin-Api-Key}; either way the acting admin needs {@code events:import}.
 * Answers 200 with a result per item even when some items failed (partial success).
 */
@Validated
@RestController
@RequestMapping("/v1/api/admin/events")
@RequiredArgsConstructor
public class LocalEventImportController {

    private final LocalEventImportService importService;
    private final LocalEventImportBatchService batchService;

    @PostMapping("/import")
    @PreAuthorize("@adminAuthorization.can(authentication,'events','import')")
    public ResponseEntity<BaseResponse<LocalEventImportResponse>> importEvents(
            @CurrentUser UUID userId,
            @RequestAttribute(name = AdminApiKeyAuthenticationFilter.API_KEY_ID_ATTRIBUTE, required = false) UUID apiKeyId,
            @Valid @RequestBody LocalEventImportRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(importService.importEvents(request, userId, apiKeyId)));
    }

    @GetMapping("/import-batches")
    @PreAuthorize("@adminAuthorization.can(authentication,'events','get') || @adminAuthorization.can(authentication,'events','import')")
    public ResponseEntity<BaseResponse<PageResponse<LocalEventImportBatchResponse>>> batches(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(batchService.list(page, size)));
    }

    @GetMapping("/import-batches/{batchId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'events','get') || @adminAuthorization.can(authentication,'events','import')")
    public ResponseEntity<BaseResponse<LocalEventImportBatchDetailResponse>> batch(@PathVariable UUID batchId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(batchService.detail(batchId)));
    }
}
