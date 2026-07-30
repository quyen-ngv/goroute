package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateHostOrganizationRequest;
import com.ds.goroute.dto.request.UpdateHostOrganizationRequest;
import com.ds.goroute.dto.request.UpsertOrganizationMemberRequest;
import com.ds.goroute.dto.request.UpsertOrganizationMemberScopeRequest;
import com.ds.goroute.dto.request.ProvisionPartnerMemberRequest;
import com.ds.goroute.dto.response.HostOrganizationResponse;
import com.ds.goroute.dto.response.OrganizationMemberResponse;
import com.ds.goroute.dto.response.OrganizationMemberScopeResponse;
import com.ds.goroute.dto.response.PartnerMemberProvisionResponse;
import com.ds.goroute.service.HostOrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/partner/organizations")
@RequiredArgsConstructor
public class PartnerOrganizationController {
    private final HostOrganizationService service;

    @GetMapping
    public ResponseEntity<BaseResponse<List<HostOrganizationResponse>>> list(Authentication auth) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.listMine(userId(auth))));
    }

    @GetMapping("/{organizationId}")
    public ResponseEntity<BaseResponse<HostOrganizationResponse>> get(Authentication auth, @PathVariable UUID organizationId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.getMine(userId(auth), organizationId)));
    }

    @PutMapping("/{organizationId}")
    public ResponseEntity<BaseResponse<HostOrganizationResponse>> update(Authentication auth, @PathVariable UUID organizationId,
            @Valid @RequestBody UpdateHostOrganizationRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.update(userId(auth), organizationId, request)));
    }

    @GetMapping("/{organizationId}/members")
    public ResponseEntity<BaseResponse<List<OrganizationMemberResponse>>> members(Authentication auth, @PathVariable UUID organizationId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.listMembers(userId(auth), organizationId)));
    }

    @PutMapping("/{organizationId}/members")
    public ResponseEntity<BaseResponse<OrganizationMemberResponse>> upsertMember(Authentication auth, @PathVariable UUID organizationId,
            @Valid @RequestBody UpsertOrganizationMemberRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.upsertMember(userId(auth), organizationId, request)));
    }

    @PostMapping("/{organizationId}/members/provision")
    public ResponseEntity<BaseResponse<PartnerMemberProvisionResponse>> provisionMember(Authentication auth,
            @PathVariable UUID organizationId, @Valid @RequestBody ProvisionPartnerMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(
                service.provisionMember(userId(auth), organizationId, request)));
    }

    @PatchMapping("/{organizationId}/members/{memberUserId}/status")
    public ResponseEntity<BaseResponse<Void>> memberStatus(Authentication auth, @PathVariable UUID organizationId,
            @PathVariable UUID memberUserId, @RequestParam String status) {
        service.updateMemberStatus(userId(auth), organizationId, memberUserId, status);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }

    @GetMapping("/{organizationId}/members/{memberUserId}/scopes")
    public ResponseEntity<BaseResponse<List<OrganizationMemberScopeResponse>>> scopes(Authentication auth,
            @PathVariable UUID organizationId,@PathVariable UUID memberUserId){
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.listMemberScopes(userId(auth),organizationId,memberUserId)));
    }

    @PostMapping("/{organizationId}/members/{memberUserId}/scopes")
    public ResponseEntity<BaseResponse<OrganizationMemberScopeResponse>> createScope(Authentication auth,
            @PathVariable UUID organizationId,@PathVariable UUID memberUserId,
            @Valid @RequestBody UpsertOrganizationMemberScopeRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(
                service.upsertMemberScope(userId(auth),organizationId,memberUserId,null,request)));
    }

    @PutMapping("/{organizationId}/members/{memberUserId}/scopes/{scopeId}")
    public ResponseEntity<BaseResponse<OrganizationMemberScopeResponse>> updateScope(Authentication auth,
            @PathVariable UUID organizationId,@PathVariable UUID memberUserId,@PathVariable UUID scopeId,
            @Valid @RequestBody UpsertOrganizationMemberScopeRequest request){
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                service.upsertMemberScope(userId(auth),organizationId,memberUserId,scopeId,request)));
    }

    @DeleteMapping("/{organizationId}/members/{memberUserId}/scopes/{scopeId}")
    public ResponseEntity<BaseResponse<Void>> deleteScope(Authentication auth,@PathVariable UUID organizationId,
            @PathVariable UUID memberUserId,@PathVariable UUID scopeId){
        service.deleteMemberScope(userId(auth),organizationId,memberUserId,scopeId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }

    private UUID userId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof UUID uuid ? uuid : UUID.fromString(principal.toString());
    }
}
