package com.ds.goroute.localevent.web;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.localevent.dto.AdminLocalEventDetailResponse;
import com.ds.goroute.localevent.dto.AdminLocalEventRow;
import com.ds.goroute.localevent.dto.LocalEventPublishStatusRequest;
import com.ds.goroute.localevent.dto.LocalEventUpsertRequest;
import com.ds.goroute.localevent.service.LocalEventAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Console management of local events (epic 17). Admins are the only authors of events: they
 * create/edit here, or import through {@link LocalEventImportController}.
 */
@Validated
@RestController
@RequestMapping("/v1/api/admin/events")
@RequiredArgsConstructor
public class LocalEventAdminController {

    private final LocalEventAdminService adminService;

    /** Comma lists are accepted for publishStatus, eventStatus and category. Dates: YYYY-MM-DD or ISO with offset. */
    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'events','get')")
    public ResponseEntity<BaseResponse<PageResponse<AdminLocalEventRow>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String publishStatus,
            @RequestParam(required = false) String eventStatus,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) String startFrom,
            @RequestParam(required = false) String startTo,
            @RequestParam(defaultValue = "false") boolean duplicatesOnly,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(adminService.list(search, publishStatus, eventStatus,
                category, source, provinceCode, startFrom, startTo, duplicatesOnly, sort, direction, page, size)));
    }

    /** The crawler's read-back by natural key; also answers for a deleted event ({@code deleted: true}). */
    @GetMapping("/lookup")
    @PreAuthorize("@adminAuthorization.can(authentication,'events','get') || @adminAuthorization.can(authentication,'events','import')")
    public ResponseEntity<BaseResponse<AdminLocalEventDetailResponse>> lookup(
            @RequestParam String source,
            @RequestParam String externalId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(adminService.lookup(source, externalId)));
    }

    @GetMapping("/{eventId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'events','get')")
    public ResponseEntity<BaseResponse<AdminLocalEventDetailResponse>> detail(@PathVariable UUID eventId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(adminService.detail(eventId)));
    }

    @PostMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'events','create')")
    public ResponseEntity<BaseResponse<AdminLocalEventDetailResponse>> create(
            @CurrentUser UUID userId,
            @Valid @RequestBody LocalEventUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.ofSucceeded(adminService.create(request, userId)));
    }

    @PutMapping("/{eventId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'events','update')")
    public ResponseEntity<BaseResponse<AdminLocalEventDetailResponse>> update(
            @CurrentUser UUID userId,
            @PathVariable UUID eventId,
            @Valid @RequestBody LocalEventUpsertRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(adminService.update(eventId, request, userId)));
    }

    @PostMapping("/{eventId}/publish-status")
    @PreAuthorize("@adminAuthorization.can(authentication,'events','update')")
    public ResponseEntity<BaseResponse<AdminLocalEventDetailResponse>> changePublishStatus(
            @CurrentUser UUID userId,
            @PathVariable UUID eventId,
            @Valid @RequestBody LocalEventPublishStatusRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(adminService.changePublishStatus(eventId, request, userId)));
    }

    @DeleteMapping("/{eventId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'events','delete')")
    public ResponseEntity<BaseResponse<Void>> delete(@CurrentUser UUID userId, @PathVariable UUID eventId) {
        adminService.delete(eventId, userId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }

    @PostMapping("/{eventId}/images/retry")
    @PreAuthorize("@adminAuthorization.can(authentication,'events','update')")
    public ResponseEntity<BaseResponse<AdminLocalEventDetailResponse>> retryImages(@PathVariable UUID eventId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(adminService.retryImages(eventId)));
    }

    /** Multipart {@code file}; returns the stored image URL to put in {@code images[].url}. */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @PreAuthorize("@adminAuthorization.can(authentication,'events','create') || @adminAuthorization.can(authentication,'events','update')")
    public ResponseEntity<BaseResponse<String>> upload(
            @CurrentUser UUID userId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(adminService.uploadImage(file, userId)));
    }
}
