package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AdminBroadcastNotificationRequest;
import com.ds.goroute.dto.request.AdminNotificationAudienceRequest;
import com.ds.goroute.dto.response.AdminNotificationAudienceResponse;
import com.ds.goroute.dto.response.AdminNotificationHistoryResponse;
import com.ds.goroute.dto.response.AdminNotificationRecipientResponse;
import com.ds.goroute.dto.response.AdminPushNotificationResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.service.AdminNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin announcements from the console.
 *
 * <p>Preview and send take the same audience payload on purpose: the number the operator
 * approved is produced by the same query that later resolves the recipients.
 */
@RestController
@RequestMapping("/v1/api/admin/notifications")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AdminNotificationController extends BaseController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminNotificationService adminNotificationService;

    @PostMapping("/audience/preview")
    @PreAuthorize("@adminAuthorization.can(authentication,'notifications','get')")
    @Operation(summary = "Resolve an audience without sending anything")
    public ResponseEntity<BaseResponse<AdminNotificationAudienceResponse>> previewAudience(
            @Valid @RequestBody AdminNotificationAudienceRequest request,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int sampleSize) {
        return ResponseEntity.ok(ofSucceeded(adminNotificationService.previewAudience(request, sampleSize)));
    }

    @PostMapping("/broadcast")
    @PreAuthorize("@adminAuthorization.can(authentication,'notifications','create')")
    @Operation(summary = "Send an announcement to an audience")
    public ResponseEntity<BaseResponse<AdminPushNotificationResponse>> broadcast(
            @CurrentUser UUID adminUserId,
            @Valid @RequestBody AdminBroadcastNotificationRequest request) {
        log.info("Admin {} sending announcement '{}' to audience {}",
                adminUserId, request.getTitle(), request.getAudience().getAudience());
        return ResponseEntity.ok(ofSucceeded(adminNotificationService.broadcast(request)));
    }

    @GetMapping("/recipients")
    @PreAuthorize("@adminAuthorization.can(authentication,'notifications','get')")
    @Operation(summary = "Search users to address an announcement to")
    public ResponseEntity<BaseResponse<PageResponse<AdminNotificationRecipientResponse>>> recipients(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return ResponseEntity.ok(ofSucceeded(adminNotificationService.searchRecipients(search, page, size)));
    }

    @GetMapping("/history")
    @PreAuthorize("@adminAuthorization.can(authentication,'notifications','get')")
    @Operation(summary = "Announcements already sent, grouped by batch")
    public ResponseEntity<BaseResponse<PageResponse<AdminNotificationHistoryResponse>>> history(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return ResponseEntity.ok(ofSucceeded(adminNotificationService.history(search, page, size)));
    }
}
