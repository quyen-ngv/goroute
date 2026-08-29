package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.ModerationPreviewRequest;
import com.ds.goroute.dto.request.ResolveModerationFlagRequest;
import com.ds.goroute.dto.request.UpsertModerationTermRequest;
import com.ds.goroute.dto.response.ModerationFlagResponse;
import com.ds.goroute.dto.response.ModerationMetricsResponse;
import com.ds.goroute.dto.response.ModerationPreviewResponse;
import com.ds.goroute.dto.response.ModerationTermAuditResponse;
import com.ds.goroute.dto.response.ModerationTermResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.ModerationAdminService;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationCategory;
import com.ds.goroute.type.ModerationFlagSource;
import com.ds.goroute.type.ModerationFlagStatus;
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
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Operator console for epic 12: term list, review queue and measurement. */
@RestController
@RequestMapping("/v1/api/admin/moderation")
@RequiredArgsConstructor
@Validated
public class AdminModerationController extends BaseService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_METRICS_DAYS = 365;

    private final ModerationAdminService service;

    // --- MOD-02 ---------------------------------------------------------------------

    @GetMapping("/terms")
    @PreAuthorize("@adminAuthorization.can(authentication,'moderation-terms','get')")
    public ResponseEntity<BaseResponse<PageResponse<ModerationTermResponse>>> terms(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isExemption,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        List<ModerationTermResponse> items = service.listTerms(q, category, isExemption, active, page, size);
        long total = service.countTerms(q, category, isExemption, active);
        return ResponseEntity.ok(ofSucceeded(PageResponse.of(items, total, page, size)));
    }

    @PostMapping("/terms")
    @PreAuthorize("@adminAuthorization.can(authentication,'moderation-terms','create')")
    public ResponseEntity<BaseResponse<ModerationTermResponse>> createTerm(
            @Valid @RequestBody UpsertModerationTermRequest request,
            @RequestAttribute UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(service.createTerm(userId, request)));
    }

    @PutMapping("/terms/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'moderation-terms','update')")
    public ResponseEntity<BaseResponse<ModerationTermResponse>> updateTerm(
            @PathVariable UUID id,
            @Valid @RequestBody UpsertModerationTermRequest request,
            @RequestAttribute UUID userId) {
        return ResponseEntity.ok(ofSucceeded(service.updateTerm(userId, id, request)));
    }

    @DeleteMapping("/terms/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'moderation-terms','delete')")
    public ResponseEntity<BaseResponse<Void>> deleteTerm(@PathVariable UUID id,
                                                         @RequestAttribute UUID userId) {
        service.deleteTerm(userId, id);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @GetMapping("/terms/{id}/audit")
    @PreAuthorize("@adminAuthorization.can(authentication,'moderation-terms','get')")
    public ResponseEntity<BaseResponse<List<ModerationTermAuditResponse>>> termAudit(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int limit) {
        return ResponseEntity.ok(ofSucceeded(service.termAudit(id, limit)));
    }

    /**
     * Trying a passage before saving a change is the only way an operator finds out that
     * a new entry causes false blocks before real users do.
     */
    @PostMapping("/terms/preview")
    @PreAuthorize("@adminAuthorization.can(authentication,'moderation-terms','get')")
    public ResponseEntity<BaseResponse<ModerationPreviewResponse>> previewTerms(
            @Valid @RequestBody ModerationPreviewRequest request) {
        return ResponseEntity.ok(ofSucceeded(service.preview(request)));
    }

    // --- MOD-06 ---------------------------------------------------------------------

    @GetMapping("/queue")
    @PreAuthorize("@adminAuthorization.can(authentication,'moderation-queue','get')")
    public ResponseEntity<BaseResponse<PageResponse<ModerationFlagResponse>>> queue(
            @RequestParam(defaultValue = "PENDING") ModerationFlagStatus status,
            @RequestParam(required = false) ModeratedContentType contentType,
            @RequestParam(required = false) ModerationCategory category,
            @RequestParam(required = false) ModerationFlagSource source,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        List<ModerationFlagResponse> items = service.queue(name(status), name(contentType),
                name(category), name(source), page, size);
        long total = service.countQueue(name(status), name(contentType), name(category), name(source));
        return ResponseEntity.ok(ofSucceeded(PageResponse.of(items, total, page, size)));
    }

    @PostMapping("/queue/{id}/resolve")
    @PreAuthorize("@adminAuthorization.can(authentication,'moderation-queue','update')")
    public ResponseEntity<BaseResponse<ModerationFlagResponse>> resolve(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveModerationFlagRequest request,
            @RequestAttribute UUID userId) {
        return ResponseEntity.ok(ofSucceeded(service.resolve(userId, id, request)));
    }

    // --- MOD-08 ---------------------------------------------------------------------

    @GetMapping("/metrics")
    @PreAuthorize("@adminAuthorization.can(authentication,'moderation-metrics','get')")
    public ResponseEntity<BaseResponse<ModerationMetricsResponse>> metrics(
            @RequestParam(defaultValue = "7") @Min(1) @Max(MAX_METRICS_DAYS) int days) {
        return ResponseEntity.ok(ofSucceeded(service.metrics(days)));
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
