package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateUserCheckinRequest;
import com.ds.goroute.dto.request.HandOverManagedAccountRequest;
import com.ds.goroute.dto.request.ManagedAccountRequest;
import com.ds.goroute.dto.request.UpdateUserCheckinRequest;
import com.ds.goroute.dto.response.ManagedAccountResponse;
import com.ds.goroute.dto.response.ManagedCheckinMediaResponse;
import com.ds.goroute.dto.response.SlicePageResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.service.ManagedAccountService;
import com.ds.goroute.type.ManagedAccountStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Managed accounts (epic 20, .docs/BLOG_POSTS_PLAN_2026-11-10.md §6): accounts the team holds,
 * posts check-ins for through the user's own check-in use case, and hands over to their owner.
 */
@RestController
@RequestMapping("/v1/api/admin/managed-accounts")
@RequiredArgsConstructor
@Validated
public class AdminManagedAccountController extends BaseController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ManagedAccountService service;

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'managed-accounts','get')")
    public ResponseEntity<BaseResponse<SlicePageResponse<ManagedAccountResponse>>> list(
            @RequestParam(required = false) @Size(max = 200) String search,
            @RequestParam(defaultValue = "MANAGED") ManagedAccountStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return ResponseEntity.ok(ofSucceeded(service.list(search, status, page, size)));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'managed-accounts','get')")
    public ResponseEntity<BaseResponse<ManagedAccountResponse>> get(@PathVariable UUID userId) {
        return ResponseEntity.ok(ofSucceeded(service.get(userId)));
    }

    @PostMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'managed-accounts','create')")
    public ResponseEntity<BaseResponse<ManagedAccountResponse>> create(
            @Valid @RequestBody ManagedAccountRequest request,
            @CurrentUser UUID adminId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ofSucceeded(service.create(adminId, request)));
    }

    @PutMapping("/{userId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'managed-accounts','update')")
    public ResponseEntity<BaseResponse<ManagedAccountResponse>> update(
            @PathVariable UUID userId,
            @Valid @RequestBody ManagedAccountRequest request,
            @CurrentUser UUID adminId) {
        return ResponseEntity.ok(ofSucceeded(service.update(adminId, userId, request)));
    }

    /** Real email (and optionally a password); the console can never act for the account again. */
    @PostMapping("/{userId}/handover")
    @PreAuthorize("@adminAuthorization.can(authentication,'managed-accounts','update')")
    public ResponseEntity<BaseResponse<ManagedAccountResponse>> handOver(
            @PathVariable UUID userId,
            @Valid @RequestBody HandOverManagedAccountRequest request,
            @CurrentUser UUID adminId) {
        return ResponseEntity.ok(ofSucceeded(service.handOver(adminId, userId, request)));
    }

    @PostMapping("/{userId}/checkins")
    @PreAuthorize("@adminAuthorization.can(authentication,'managed-accounts','update')")
    public ResponseEntity<BaseResponse<UserCheckinResponse>> createCheckin(
            @PathVariable UUID userId,
            @Valid @RequestBody CreateUserCheckinRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ofSucceeded(service.createCheckin(userId, request)));
    }

    @PutMapping("/{userId}/checkins/{checkinId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'managed-accounts','update')")
    public ResponseEntity<BaseResponse<UserCheckinResponse>> updateCheckin(
            @PathVariable UUID userId,
            @PathVariable UUID checkinId,
            @Valid @RequestBody UpdateUserCheckinRequest request) {
        return ResponseEntity.ok(ofSucceeded(service.updateCheckin(userId, checkinId, request)));
    }

    @DeleteMapping("/{userId}/checkins/{checkinId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'managed-accounts','update')")
    public ResponseEntity<BaseResponse<Void>> deleteCheckin(
            @PathVariable UUID userId,
            @PathVariable UUID checkinId) {
        service.deleteCheckin(userId, checkinId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    /**
     * One file for a check-in of this account: a video (stored as that account's check-in video) or
     * an image (a photo, or a video's poster). Answers {@code {url, mediaType, durationSeconds}}.
     */
    @PostMapping(value = "/{userId}/checkin-media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@adminAuthorization.can(authentication,'managed-accounts','update')")
    public ResponseEntity<BaseResponse<ManagedCheckinMediaResponse>> uploadCheckinMedia(
            @PathVariable UUID userId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ofSucceeded(service.uploadCheckinMedia(userId, file)));
    }
}
