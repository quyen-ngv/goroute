package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.GuideProfileResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.service.GuideBookingService;
import com.ds.goroute.service.GuideDirectoryService;
import com.ds.goroute.type.GuideProfileStatus;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Guide verification and money controls (GUIDE-01, GUIDE-05). */
@RestController
@RequestMapping("/v1/api/admin/guides")
@RequiredArgsConstructor
@Validated
public class AdminGuideController extends BaseController {

    private static final int MAX_PAGE_SIZE = 100;

    private final GuideDirectoryService directoryService;
    private final GuideBookingService bookingService;

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'guides','get')")
    public ResponseEntity<BaseResponse<PageResponse<GuideProfileResponse>>> queue(
            @RequestParam(defaultValue = "PENDING_VERIFICATION") GuideProfileStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        List<GuideProfileResponse> items = directoryService.verificationQueue(status, page, size);
        return ResponseEntity.ok(ofSucceeded(
                PageResponse.of(items, directoryService.countVerificationQueue(status), page, size)));
    }

    /**
     * Identity documents. Behind its own permission and read separately from the profile,
     * because seeing somebody's papers should be a deliberate act rather than a side effect
     * of opening their application.
     */
    @GetMapping("/{guideId}/documents")
    @PreAuthorize("@adminAuthorization.can(authentication,'guides','update')")
    public ResponseEntity<BaseResponse<List<Map<String, Object>>>> documents(@PathVariable UUID guideId) {
        return ResponseEntity.ok(ofSucceeded(directoryService.identityDocuments(guideId)));
    }

    @PostMapping("/{guideId}/decide")
    @PreAuthorize("@adminAuthorization.can(authentication,'guides','update')")
    public ResponseEntity<BaseResponse<GuideProfileResponse>> decide(
            @PathVariable UUID guideId,
            @RequestParam GuideProfileStatus status,
            @RequestParam(required = false) @Size(max = 2000) String decisionNote,
            @RequestParam(required = false) @Size(max = 2000) String informationRequested,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(
                directoryService.decide(userId, guideId, status, decisionNote, informationRequested)));
    }

    /** Holds a payout while a disagreement is being looked at. */
    @PostMapping("/bookings/{bookingId}/freeze-payout")
    @PreAuthorize("@adminAuthorization.can(authentication,'guides','update')")
    public ResponseEntity<BaseResponse<Void>> freezePayout(
            @PathVariable UUID bookingId,
            @RequestParam boolean frozen,
            @CurrentUser UUID userId) {
        bookingService.freezePayout(userId, bookingId, frozen);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
