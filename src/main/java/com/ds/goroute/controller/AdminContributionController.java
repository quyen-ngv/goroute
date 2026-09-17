package com.ds.goroute.controller;

import com.ds.goroute.dto.request.RejectContributionRequest;
import com.ds.goroute.dto.response.AdminContributionGroupResponse;
import com.ds.goroute.service.PlaceContributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/contributions")
@RequiredArgsConstructor
public class AdminContributionController extends BaseController {

    private final PlaceContributionService contributionService;

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'contributions','get')")
    public ResponseEntity listGroups(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<AdminContributionGroupResponse> items = contributionService.adminListGroups(status, page, size);
        return ResponseEntity.ok(ofSucceeded(items));
    }

    @GetMapping("/{groupId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'contributions','get')")
    public ResponseEntity getGroup(@PathVariable UUID groupId) {
        AdminContributionGroupResponse response = contributionService.adminGetGroup(groupId);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping("/{groupId}/approve")
    @PreAuthorize("@adminAuthorization.can(authentication,'contributions','update')")
    public ResponseEntity approve(@PathVariable UUID groupId) {
        contributionService.adminApprove(groupId);
        AdminContributionGroupResponse response = contributionService.adminGetGroup(groupId);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping("/{groupId}/reject")
    @PreAuthorize("@adminAuthorization.can(authentication,'contributions','update')")
    public ResponseEntity reject(
            @PathVariable UUID groupId,
            @RequestBody(required = false) RejectContributionRequest request) {
        String reason = request != null ? request.getReason() : null;
        contributionService.adminReject(groupId, reason);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/{groupId}/sync")
    @PreAuthorize("@adminAuthorization.can(authentication,'contributions','update')")
    public ResponseEntity sync(@PathVariable UUID groupId) {
        contributionService.syncScrapingGroup(groupId);
        AdminContributionGroupResponse response = contributionService.adminGetGroup(groupId);
        return ResponseEntity.ok(ofSucceeded(response));
    }
}
