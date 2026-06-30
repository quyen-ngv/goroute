package com.ds.goroute.controller;

import com.ds.goroute.dto.request.RejectContributionRequest;
import com.ds.goroute.dto.response.AdminContributionGroupResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.PlaceContributionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/contributions")
@RequiredArgsConstructor
@Tag(name = "Admin Contributions", description = "Admin contribution moderation APIs")
public class AdminContributionController extends BaseService {

    private final PlaceContributionService contributionService;

    @GetMapping
    @Operation(summary = "List contribution groups by status")
    public ResponseEntity listGroups(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<AdminContributionGroupResponse> items = contributionService.adminListGroups(status, page, size);
        return ResponseEntity.ok(ofSucceeded(items));
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "Get contribution group detail")
    public ResponseEntity getGroup(@PathVariable UUID groupId) {
        AdminContributionGroupResponse response = contributionService.adminGetGroup(groupId);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping("/{groupId}/approve")
    @Operation(summary = "Approve contribution group and trigger scrape/import")
    public ResponseEntity approve(@PathVariable UUID groupId) {
        contributionService.adminApprove(groupId);
        AdminContributionGroupResponse response = contributionService.adminGetGroup(groupId);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping("/{groupId}/reject")
    @Operation(summary = "Reject contribution group")
    public ResponseEntity reject(
            @PathVariable UUID groupId,
            @RequestBody(required = false) RejectContributionRequest request) {
        String reason = request != null ? request.getReason() : null;
        contributionService.adminReject(groupId, reason);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/{groupId}/sync")
    @Operation(summary = "Poll scrape job status and sync group state")
    public ResponseEntity sync(@PathVariable UUID groupId) {
        contributionService.syncScrapingGroup(groupId);
        AdminContributionGroupResponse response = contributionService.adminGetGroup(groupId);
        return ResponseEntity.ok(ofSucceeded(response));
    }
}
