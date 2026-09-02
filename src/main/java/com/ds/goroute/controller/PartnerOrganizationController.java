package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.CreateHostOrganizationRequest;
import com.ds.goroute.dto.request.UpdateHostOrganizationRequest;
import com.ds.goroute.dto.request.UpsertOrganizationMemberRequest;
import com.ds.goroute.dto.request.UpsertOrganizationMemberScopeRequest;
import com.ds.goroute.dto.request.ProvisionPartnerMemberRequest;
import com.ds.goroute.dto.request.OpenStatementDisputeRequest;
import com.ds.goroute.dto.request.UpdatePartnerBillingRequest;
import com.ds.goroute.dto.response.HostOrganizationResponse;
import com.ds.goroute.dto.response.OrganizationVerificationDocumentResponse;
import com.ds.goroute.dto.response.OrganizationVerificationResponse;
import com.ds.goroute.dto.response.PartnerQualityResponse;
import com.ds.goroute.dto.response.OrganizationMemberResponse;
import com.ds.goroute.dto.response.OrganizationMemberScopeResponse;
import com.ds.goroute.dto.response.PartnerMemberProvisionResponse;
import com.ds.goroute.dto.response.PartnerAccessResponse;
import com.ds.goroute.dto.response.PartnerOrganizationSummaryResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.dto.response.PartnerFinanceSummaryResponse;
import com.ds.goroute.dto.response.PartnerStatementResponse;
import com.ds.goroute.service.HostOrganizationService;
import com.ds.goroute.service.OrganizationVerificationService;
import com.ds.goroute.service.PartnerDashboardService;
import com.ds.goroute.service.PartnerQualityService;
import com.ds.goroute.service.PartnerStatementService;
import com.ds.goroute.type.OrganizationVerificationDocumentKind;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import com.ds.goroute.type.OrganizationMemberStatus;
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
    private final PartnerDashboardService dashboardService;
    private final OrganizationVerificationService verificationService;
    private final PartnerQualityService qualityService;
    private final PartnerStatementService statementService;

    /**
     * Self-serve organization creation by any signed-in app user; the caller becomes owner and
     * thereby a partner. SecurityConfig opens exactly this POST to authenticated users, ahead of the
     * ROLE_PARTNER rule that guards the rest of /v1/api/partner/**.
     */
    @PostMapping
    public ResponseEntity<BaseResponse<HostOrganizationResponse>> create(Authentication auth,
            @Valid @RequestBody CreateHostOrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.create(userId(auth), request)));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<List<HostOrganizationResponse>>> list(Authentication auth) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.listMine(userId(auth))));
    }

    @GetMapping("/{organizationId}")
    public ResponseEntity<BaseResponse<HostOrganizationResponse>> get(Authentication auth, @PathVariable UUID organizationId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.getMine(userId(auth), organizationId)));
    }

    @GetMapping("/{organizationId}/access")
    public ResponseEntity<BaseResponse<PartnerAccessResponse>> access(Authentication auth, @PathVariable UUID organizationId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.getMyAccess(userId(auth), organizationId)));
    }

    /** Counters for the partner dashboard: pending requests, today's arrivals/departures, unread chats, unanswered reviews. */
    @GetMapping("/{organizationId}/summary")
    public ResponseEntity<BaseResponse<PartnerOrganizationSummaryResponse>> summary(Authentication auth, @PathVariable UUID organizationId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(dashboardService.summary(userId(auth), organizationId)));
    }

    // --- Verification (ORGANIZATION_READ to view, ORGANIZATION_WRITE to change) --------------------

    @GetMapping("/{organizationId}/verification")
    public ResponseEntity<BaseResponse<OrganizationVerificationResponse>> verification(Authentication auth,
            @PathVariable UUID organizationId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(verificationService.get(userId(auth), organizationId)));
    }

    /** Images only (JPEG/PNG/WEBP) -- the shared upload door rejects PDF; see the response's uploadHint. */
    @PostMapping(value = "/{organizationId}/verification/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<OrganizationVerificationDocumentResponse>> uploadVerificationDocument(Authentication auth,
            @PathVariable UUID organizationId, @RequestParam OrganizationVerificationDocumentKind kind,
            @RequestParam(required = false) String note, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(
                verificationService.uploadDocument(userId(auth), organizationId, kind, note, file)));
    }

    @DeleteMapping("/{organizationId}/verification/documents/{documentId}")
    public ResponseEntity<BaseResponse<Void>> deleteVerificationDocument(Authentication auth,
            @PathVariable UUID organizationId, @PathVariable UUID documentId) {
        verificationService.deleteDocument(userId(auth), organizationId, documentId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }

    @PostMapping("/{organizationId}/verification/submit")
    public ResponseEntity<BaseResponse<OrganizationVerificationResponse>> submitVerification(Authentication auth,
            @PathVariable UUID organizationId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(verificationService.submit(userId(auth), organizationId)));
    }

    /** Rolling 90-day quality snapshot (ORGANIZATION_READ); figures are null until the job has run. */
    @GetMapping("/{organizationId}/quality")
    public ResponseEntity<BaseResponse<PartnerQualityResponse>> quality(Authentication auth, @PathVariable UUID organizationId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(qualityService.partnerGet(userId(auth), organizationId)));
    }

    // --- Finance: statements, disputes, billing (REPORT_READ or ORGANIZATION_READ) ---------------

    @GetMapping("/{organizationId}/statements")
    public ResponseEntity<BaseResponse<PageResponse<PartnerStatementResponse>>> statements(Authentication auth,
            @PathVariable UUID organizationId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                statementService.partnerList(userId(auth), organizationId, page, size)));
    }

    @GetMapping("/{organizationId}/statements/{statementId}")
    public ResponseEntity<BaseResponse<PartnerStatementResponse>> statement(Authentication auth,
            @PathVariable UUID organizationId, @PathVariable UUID statementId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                statementService.partnerGet(userId(auth), organizationId, statementId)));
    }

    /** One row per statement line, for the partner's own bookkeeping. */
    @GetMapping(value = "/{organizationId}/statements/{statementId}/export", produces = "text/csv")
    public ResponseEntity<String> exportStatement(Authentication auth, @PathVariable UUID organizationId,
            @PathVariable UUID statementId) {
        String csv = statementService.partnerExportCsv(userId(auth), organizationId, statementId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"statement-" + statementId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    @PostMapping("/{organizationId}/statements/{statementId}/lines/{lineId}/dispute")
    public ResponseEntity<BaseResponse<PartnerStatementResponse>> openDispute(Authentication auth,
            @PathVariable UUID organizationId, @PathVariable UUID statementId, @PathVariable UUID lineId,
            @Valid @RequestBody OpenStatementDisputeRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(statementService.partnerOpenDispute(
                userId(auth), organizationId, statementId, lineId, request.getReason())));
    }

    /** Live accrual for the month in progress; the dashboard numbers stay in /summary. */
    @GetMapping("/{organizationId}/finance/summary")
    public ResponseEntity<BaseResponse<PartnerFinanceSummaryResponse>> financeSummary(Authentication auth,
            @PathVariable UUID organizationId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(statementService.partnerSummary(userId(auth), organizationId)));
    }

    @PutMapping("/{organizationId}/billing")
    public ResponseEntity<BaseResponse<HostOrganizationResponse>> updateBilling(Authentication auth,
            @PathVariable UUID organizationId, @Valid @RequestBody UpdatePartnerBillingRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.updateBilling(userId(auth), organizationId, request)));
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
            @PathVariable UUID memberUserId, @RequestParam OrganizationMemberStatus status) {
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
