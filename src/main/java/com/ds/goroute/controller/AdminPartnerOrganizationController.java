package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.UpdateOrganizationStatusRequest;
import com.ds.goroute.dto.request.AdminProvisionPartnerRequest;
import com.ds.goroute.dto.request.ProvisionPartnerMemberRequest;
import com.ds.goroute.dto.request.UpdateHostOrganizationRequest;
import com.ds.goroute.dto.request.UpsertOrganizationMemberRequest;
import com.ds.goroute.dto.request.UpsertOrganizationMemberScopeRequest;
import com.ds.goroute.dto.response.HostOrganizationResponse;
import com.ds.goroute.dto.response.OrganizationMemberResponse;
import com.ds.goroute.dto.response.OrganizationMemberScopeResponse;
import com.ds.goroute.dto.response.PartnerProvisionResponse;
import com.ds.goroute.dto.response.PartnerMemberProvisionResponse;
import com.ds.goroute.service.HostOrganizationService;
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

    @PostMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','create')")
    public ResponseEntity<BaseResponse<PartnerProvisionResponse>> create(Authentication authentication,
            @Valid @RequestBody AdminProvisionPartnerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(
                service.adminProvision(actor(authentication), request)));
    }

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'partner-organizations','get')")
    public ResponseEntity<BaseResponse<List<HostOrganizationResponse>>> list(
            @RequestParam(required = false) String q, @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminList(q != null ? q : search, status, page, size)));
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
            @PathVariable UUID organizationId, @RequestParam(required=false) String reason) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminDisable(actor(authentication), organizationId, reason)));
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
            @PathVariable UUID memberUserId, @RequestParam String status) {
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
    public ResponseEntity<BaseResponse<HostOrganizationResponse>> updateStatus(@PathVariable UUID organizationId,
            @Valid @RequestBody UpdateOrganizationStatusRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminUpdateStatus(
                organizationId, request.getOperationalStatus(), request.getVerificationStatus())));
    }

    private UUID actor(Authentication authentication) { return UUID.fromString(authentication.getName()); }
}
