package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.DecideOrganizationVerificationRequest;
import com.ds.goroute.dto.request.UpdateOrganizationStatusRequest;
import com.ds.goroute.dto.request.AdminProvisionPartnerRequest;
import com.ds.goroute.dto.request.ProvisionPartnerMemberRequest;
import com.ds.goroute.dto.request.UpdateHostOrganizationRequest;
import com.ds.goroute.dto.request.UpsertOrganizationMemberRequest;
import com.ds.goroute.dto.request.UpsertOrganizationMemberScopeRequest;
import com.ds.goroute.dto.request.GeneratePartnerStatementRequest;
import com.ds.goroute.dto.request.ResolveStatementDisputeRequest;
import com.ds.goroute.dto.request.UpdatePartnerCommissionRequest;
import com.ds.goroute.dto.request.UpdatePartnerStatementStatusRequest;
import com.ds.goroute.dto.response.HostOrganizationResponse;
import com.ds.goroute.dto.response.OrganizationVerificationResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.dto.response.PartnerQualityResponse;
import com.ds.goroute.dto.response.PartnerStatementResponse;
import com.ds.goroute.dto.response.VerificationQueueItemResponse;
import com.ds.goroute.dto.response.OrganizationMemberResponse;
import com.ds.goroute.dto.response.OrganizationMemberScopeResponse;
import com.ds.goroute.dto.response.PartnerProvisionResponse;
import com.ds.goroute.dto.response.PartnerMemberProvisionResponse;
import com.ds.goroute.service.HostOrganizationService;
import com.ds.goroute.service.OrganizationVerificationService;
import com.ds.goroute.service.PartnerQualityService;
import com.ds.goroute.service.PartnerStatementService;
import com.ds.goroute.type.OrganizationMemberStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/partner-organizations")
@RequiredArgsConstructor
public class AdminPartnerOrganizationController {
    private final HostOrganizationService service;
    private final OrganizationVerificationService verificationService;
    private final PartnerQualityService qualityService;
    private final PartnerStatementService statementService;

    @PostMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','create')")
    public ResponseEntity<BaseResponse<PartnerProvisionResponse>> create(Authentication authentication,
            @Valid @RequestBody AdminProvisionPartnerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(
                service.adminProvision(actor(authentication), request)));
    }

    /** Columns this list may be ordered by; anything else falls back to its natural order. */
    private static final java.util.Set<String> ORGANIZATION_SORT_FIELDS = java.util.Set.of(
            "displayName", "legalName", "updatedAt", "createdAt");

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','get')")
    public ResponseEntity<BaseResponse<List<HostOrganizationResponse>>> list(
            @RequestParam(required = false) String q, @RequestParam(required = false) String search,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> organizationType,
            @RequestParam(required = false) List<String> verificationStatus,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminList(q != null ? q : search, com.ds.goroute.utils.AdminListSort.codes(status),
                com.ds.goroute.utils.AdminListSort.codes(organizationType), com.ds.goroute.utils.AdminListSort.codes(verificationStatus),
                com.ds.goroute.utils.AdminListSort.field(sort, ORGANIZATION_SORT_FIELDS),
                com.ds.goroute.utils.AdminListSort.descending(direction), page, size)));
    }

    /** Organizations awaiting a verification decision, oldest submission first. Literal path: must not be swallowed by /{organizationId}. */
    @GetMapping("/verification-queue")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','get')")
    public ResponseEntity<BaseResponse<PageResponse<VerificationQueueItemResponse>>> verificationQueue(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(verificationService.adminQueue(page, size)));
    }

    @GetMapping("/{organizationId}/verification")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','get')")
    public ResponseEntity<BaseResponse<OrganizationVerificationResponse>> verification(@PathVariable UUID organizationId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(verificationService.adminGet(organizationId)));
    }

    @PostMapping("/{organizationId}/verification/decide")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','update')")
    public ResponseEntity<BaseResponse<HostOrganizationResponse>> decideVerification(Authentication authentication,
            @PathVariable UUID organizationId, @Valid @RequestBody DecideOrganizationVerificationRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                verificationService.adminDecide(actor(authentication), organizationId, request)));
    }

    @GetMapping("/{organizationId}/quality")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','get')")
    public ResponseEntity<BaseResponse<PartnerQualityResponse>> quality(@PathVariable UUID organizationId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(qualityService.adminGet(organizationId)));
    }

    @PostMapping("/{organizationId}/quality/recompute")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','update')")
    public ResponseEntity<BaseResponse<PartnerQualityResponse>> recomputeQuality(@PathVariable UUID organizationId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(qualityService.adminRecompute(organizationId)));
    }

    // --- Finance: statements, disputes, commission -----------------------------------------------

    @GetMapping("/{organizationId}/statements")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','get')")
    public ResponseEntity<BaseResponse<PageResponse<PartnerStatementResponse>>> statements(
            @PathVariable UUID organizationId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(statementService.adminList(organizationId, page, size)));
    }

    @GetMapping("/{organizationId}/statements/{statementId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','get')")
    public ResponseEntity<BaseResponse<PartnerStatementResponse>> statement(@PathVariable UUID organizationId,
            @PathVariable UUID statementId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(statementService.adminGet(organizationId, statementId)));
    }

    /** Generates or refreshes a period and issues it. Disputed lines survive a regeneration. */
    @PostMapping("/{organizationId}/statements/generate")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','update')")
    public ResponseEntity<BaseResponse<PartnerStatementResponse>> generateStatement(Authentication authentication,
            @PathVariable UUID organizationId, @Valid @RequestBody GeneratePartnerStatementRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(statementService.adminGenerate(actor(authentication),
                organizationId, request.getPeriodStart(), request.getPeriodEnd())));
    }

    /** Accepting zeroes the line's commission and recomputes the statement; rejecting needs a note. */
    @PostMapping("/statements/{statementId}/lines/{lineId}/resolve")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','update')")
    public ResponseEntity<BaseResponse<PartnerStatementResponse>> resolveDispute(Authentication authentication,
            @PathVariable UUID statementId, @PathVariable UUID lineId,
            @Valid @RequestBody ResolveStatementDisputeRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(statementService.adminResolveDispute(actor(authentication),
                statementId, lineId, Boolean.TRUE.equals(request.getAccept()), request.getNote())));
    }

    @PatchMapping("/statements/{statementId}/status")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','update')")
    public ResponseEntity<BaseResponse<PartnerStatementResponse>> statementStatus(Authentication authentication,
            @PathVariable UUID statementId, @Valid @RequestBody UpdatePartnerStatementStatusRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(statementService.adminUpdateStatus(actor(authentication),
                statementId, request.getStatus(), request.getNote())));
    }

    /** Only future bookings are affected: the rate is frozen on every booking already taken. */
    @PatchMapping("/{organizationId}/commission")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','update')")
    public ResponseEntity<BaseResponse<HostOrganizationResponse>> updateCommission(Authentication authentication,
            @PathVariable UUID organizationId, @Valid @RequestBody UpdatePartnerCommissionRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                service.adminUpdateCommission(actor(authentication), organizationId, request)));
    }

    @GetMapping("/{organizationId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','get')")
    public ResponseEntity<BaseResponse<HostOrganizationResponse>> get(@PathVariable UUID organizationId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminGet(organizationId)));
    }

    @PutMapping("/{organizationId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','update')")
    public ResponseEntity<BaseResponse<HostOrganizationResponse>> update(Authentication authentication,
            @PathVariable UUID organizationId, @Valid @RequestBody UpdateHostOrganizationRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminUpdate(actor(authentication), organizationId, request)));
    }

    @DeleteMapping("/{organizationId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','delete')")
    public ResponseEntity<BaseResponse<HostOrganizationResponse>> disable(Authentication authentication,
            @PathVariable UUID organizationId, @RequestParam(required=false) String reason,
            @RequestParam Long expectedVersion) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminDisable(
                actor(authentication), organizationId, reason, expectedVersion)));
    }

    @GetMapping("/{organizationId}/members")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','get')")
    public ResponseEntity<BaseResponse<List<OrganizationMemberResponse>>> members(@PathVariable UUID organizationId){
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminListMembers(organizationId)));
    }

    @PutMapping("/{organizationId}/members")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','update')")
    public ResponseEntity<BaseResponse<OrganizationMemberResponse>> upsertMember(Authentication authentication,
            @PathVariable UUID organizationId, @Valid @RequestBody UpsertOrganizationMemberRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminUpsertMember(actor(authentication), organizationId, request)));
    }

    @PostMapping("/{organizationId}/members/provision")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','create')")
    public ResponseEntity<BaseResponse<PartnerMemberProvisionResponse>> provisionMember(Authentication authentication,
            @PathVariable UUID organizationId, @Valid @RequestBody ProvisionPartnerMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(
                service.adminProvisionMember(actor(authentication), organizationId, request)));
    }

    @PatchMapping("/{organizationId}/members/{memberUserId}/status")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','update')")
    public ResponseEntity<BaseResponse<Void>> memberStatus(Authentication authentication, @PathVariable UUID organizationId,
            @PathVariable UUID memberUserId, @RequestParam OrganizationMemberStatus status) {
        service.adminUpdateMemberStatus(actor(authentication), organizationId, memberUserId, status);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }

    @GetMapping("/{organizationId}/members/{memberUserId}/scopes")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','get')")
    public ResponseEntity<BaseResponse<List<OrganizationMemberScopeResponse>>> scopes(@PathVariable UUID organizationId,
            @PathVariable UUID memberUserId){
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminListMemberScopes(organizationId,memberUserId)));
    }

    @PostMapping("/{organizationId}/members/{memberUserId}/scopes")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','update')")
    public ResponseEntity<BaseResponse<OrganizationMemberScopeResponse>> createScope(Authentication authentication,
            @PathVariable UUID organizationId, @PathVariable UUID memberUserId,
            @Valid @RequestBody UpsertOrganizationMemberScopeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(
                service.adminUpsertMemberScope(actor(authentication), organizationId, memberUserId, null, request)));
    }

    @PutMapping("/{organizationId}/members/{memberUserId}/scopes/{scopeId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','update')")
    public ResponseEntity<BaseResponse<OrganizationMemberScopeResponse>> updateScope(Authentication authentication,
            @PathVariable UUID organizationId, @PathVariable UUID memberUserId, @PathVariable UUID scopeId,
            @Valid @RequestBody UpsertOrganizationMemberScopeRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                service.adminUpsertMemberScope(actor(authentication), organizationId, memberUserId, scopeId, request)));
    }

    @DeleteMapping("/{organizationId}/members/{memberUserId}/scopes/{scopeId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','delete')")
    public ResponseEntity<BaseResponse<Void>> deleteScope(Authentication authentication, @PathVariable UUID organizationId,
            @PathVariable UUID memberUserId, @PathVariable UUID scopeId) {
        service.adminDeleteMemberScope(actor(authentication), organizationId, memberUserId, scopeId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }

    @PatchMapping("/{organizationId}/status")
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','update')")
    public ResponseEntity<BaseResponse<HostOrganizationResponse>> updateStatus(Authentication authentication,
            @PathVariable UUID organizationId,
            @Valid @RequestBody UpdateOrganizationStatusRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminUpdateStatus(
                actor(authentication), organizationId, request.getOperationalStatus(), request.getVerificationStatus(),
                request.getExpectedVersion())));
    }

    private UUID actor(Authentication authentication) { return UUID.fromString(authentication.getName()); }
}
