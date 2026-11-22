package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AiApiCallFilter;
import com.ds.goroute.dto.response.AiApiCallPageResponse;
import com.ds.goroute.dto.response.AiApiCallStatResponse;
import com.ds.goroute.entity.AiApiCall;
import com.ds.goroute.service.AiCallLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** AI call log for operators. Prompts can hold users' own words, so it needs configs:get. */
@RestController
@RequestMapping("/v1/api/admin/ai-calls")
@RequiredArgsConstructor
public class AdminAiCallController extends BaseController {
    private final AiCallLogService service;

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'configs','get')")
    public ResponseEntity<BaseResponse<AiApiCallPageResponse>> list(
            @RequestParam(required = false) String feature,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        AiApiCallFilter filter = filter(feature, operation, model, status, traceId, userId, from, to);
        return ResponseEntity.ok(ofSucceeded(service.list(filter, page, size)));
    }

    @GetMapping("/stats")
    @PreAuthorize("@adminAuthorization.can(authentication,'configs','get')")
    public ResponseEntity<BaseResponse<List<AiApiCallStatResponse>>> stats(
            @RequestParam(required = false) String feature,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ofSucceeded(service.stats(filter(feature, null, model, status, null, null, from, to))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'configs','get')")
    public ResponseEntity<BaseResponse<AiApiCall>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ofSucceeded(service.get(id)));
    }

    /** {@code to} is the last day included. */
    private static AiApiCallFilter filter(String feature, String operation, String model, String status, String traceId,
                                          UUID userId, LocalDate from, LocalDate to) {
        return AiApiCallFilter.builder()
                .feature(feature).operation(operation).model(model).status(status).traceId(traceId).userId(userId)
                .from(from == null ? null : from.atStartOfDay())
                .to(to == null ? null : to.plusDays(1).atStartOfDay())
                .build();
    }
}
