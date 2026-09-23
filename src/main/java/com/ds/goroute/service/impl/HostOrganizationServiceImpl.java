package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateHostOrganizationRequest;
import com.ds.goroute.dto.request.UpdateHostOrganizationRequest;
import com.ds.goroute.dto.request.UpdatePartnerBillingRequest;
import com.ds.goroute.dto.request.UpdatePartnerCommissionRequest;
import com.ds.goroute.dto.request.UpsertOrganizationMemberRequest;
import com.ds.goroute.dto.request.UpsertOrganizationMemberScopeRequest;
import com.ds.goroute.dto.request.AdminProvisionPartnerRequest;
import com.ds.goroute.dto.request.ProvisionPartnerMemberRequest;
import com.ds.goroute.dto.response.HostOrganizationResponse;
import com.ds.goroute.dto.response.OrganizationMemberResponse;
import com.ds.goroute.dto.response.OrganizationMemberScopeResponse;
import com.ds.goroute.dto.response.PartnerProvisionResponse;
import com.ds.goroute.dto.response.PartnerMemberProvisionResponse;
import com.ds.goroute.dto.response.PartnerAccessResponse;
import com.ds.goroute.entity.HostOrganization;
import com.ds.goroute.entity.OrganizationMember;
import com.ds.goroute.entity.OrganizationMemberScope;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.HostOrganizationRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.HotelMarketplaceRepository;
import com.ds.goroute.repository.ActivityCommerceRepository;
import com.ds.goroute.service.HostOrganizationService;
import com.ds.goroute.service.MarketplaceHistoryService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.service.UserAccountService;
import com.ds.goroute.type.AccountStatus;
import com.ds.goroute.type.OrganizationMemberStatus;
import com.ds.goroute.type.OrganizationOperationalStatus;
import com.ds.goroute.type.OrganizationType;
import com.ds.goroute.type.OrganizationVerificationStatus;
import com.ds.goroute.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Currency;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HostOrganizationServiceImpl implements HostOrganizationService {
    private static final Set<String> PARTNER_PORTAL_PERMISSIONS = Set.of(
            "ORGANIZATION_READ", "ORGANIZATION_WRITE", "HOTEL_READ", "HOTEL_WRITE", "ROOM_WRITE", "RATE_WRITE", "INVENTORY_WRITE",
            "BOOKING_READ", "BOOKING_WRITE", "ACTIVITY_READ", "ACTIVITY_WRITE", "SLOT_WRITE", "ORDER_READ", "ORDER_WRITE",
            "CHAT_WRITE", "REVIEW_RESPOND", "REPORT_READ", "MEMBER_MANAGE");
    private static final Set<String> ASSIGNABLE_PERMISSIONS = Set.of(
            "ORGANIZATION_READ", "HOTEL_READ", "HOTEL_WRITE", "ROOM_WRITE", "RATE_WRITE", "INVENTORY_WRITE",
            "BOOKING_READ", "BOOKING_WRITE", "ACTIVITY_READ", "ACTIVITY_WRITE", "SLOT_WRITE", "ORDER_READ",
            "ORDER_WRITE", "CHAT_WRITE", "REVIEW_RESPOND", "REPORT_READ", "MEMBER_MANAGE");
    private final HostOrganizationRepository repository;
    private final UserRepository userRepository;
    private final PartnerAuthorizationService authorizationService;
    private final MarketplaceHistoryService historyService;
    private final HotelMarketplaceRepository hotelRepository;
    private final ActivityCommerceRepository activityRepository;
    private final UserAccountService userAccountService;

    @Override
    @Transactional
    public HostOrganizationResponse create(UUID actorUserId, CreateHostOrganizationRequest request) {
        userRepository.findById(actorUserId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.USER_NOT_FOUND));
        validateTimezoneAndCurrency(request.getTimezone(), request.getDefaultCurrency());
        LocalDateTime now = LocalDateTime.now();
        HostOrganization organization = HostOrganization.builder()
                .id(UUID.randomUUID()).ownerUserId(actorUserId)
                .legalName(request.getLegalName().trim()).displayName(request.getDisplayName().trim())
                .organizationType(enumNameOrDefault(request.getOrganizationType(), OrganizationType.BUSINESS))
                .verificationStatus(OrganizationVerificationStatus.UNVERIFIED.name())
                .operationalStatus(OrganizationOperationalStatus.ENABLED.name())
                .defaultCurrency(valueOrDefault(request.getDefaultCurrency(), "VND").toUpperCase())
                .timezone(request.getTimezone()).contactEmail(blankToNull(request.getContactEmail()))
                .contactPhone(blankToNull(request.getContactPhone())).settings(toJsonMap(request.getSettings()))
                .dataVersion(1L).createdAt(now).updatedAt(now).build();
        repository.insert(organization);
        historyService.record(organization.getId(), "HOST_ORGANIZATION", organization.getId(), "CREATED",
                organization, List.of(), actorUserId, "USER", null);
        return toResponse(organization);
    }

    @Override
    @Transactional
    public PartnerProvisionResponse adminProvision(UUID actorUserId, AdminProvisionPartnerRequest request) {
        if ((request.getOwnerUserId() == null) == (request.getOwnerAccount() == null)) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST,
                    "Provide exactly one of ownerUserId or ownerAccount");
        }
        UUID ownerUserId = request.getOwnerUserId();
        String temporaryPassword = null;
        String ownerUsername;
        if (ownerUserId == null) {
            AdminProvisionPartnerRequest.OwnerAccount owner = request.getOwnerAccount();
            if (owner == null) throw new BusinessException(ErrorConstant.BAD_REQUEST, "ownerAccount is required when ownerUserId is empty");
            UserAccountService.ProvisionedAccount account = userAccountService.provision(
                    owner.getUsername(), owner.getEmail(), owner.getFullName(), owner.getTemporaryPassword());
            ownerUserId = account.user().getId(); ownerUsername = account.user().getUsername();
            temporaryPassword = account.temporaryPassword();
        } else {
            var owner = userRepository.findById(ownerUserId).orElseThrow(() -> new BusinessException(ErrorConstant.USER_NOT_FOUND));
            if (!AccountStatus.ACTIVE.name().equals(owner.getAccountStatus())) {
                throw new BusinessException(ErrorConstant.BAD_REQUEST, "Existing owner account must be active");
            }
            if (owner.getPasswordHash() == null) {
                throw new BusinessException(ErrorConstant.BAD_REQUEST,
                        "Existing owner cannot access the portal with a password; provision a new account instead");
            }
            ownerUsername = owner.getUsername();
        }
        HostOrganization organization = buildOrganization(ownerUserId, request.getOrganization());
        repository.insert(organization);
        historyService.record(organization.getId(), "HOST_ORGANIZATION", organization.getId(), "ADMIN_CREATED",
                organization, List.of(), actorUserId, "ADMIN", null);
        return PartnerProvisionResponse.builder().organization(toResponse(organization)).ownerUserId(ownerUserId)
                .ownerUsername(ownerUsername).temporaryPassword(temporaryPassword)
                .mustChangePassword(temporaryPassword != null).build();
    }

    @Override
    @Transactional
    public HostOrganizationResponse adminUpdate(UUID actorUserId, UUID organizationId, UpdateHostOrganizationRequest request) {
        HostOrganization organization = findRequired(organizationId);
        applyOrganizationUpdate(organization, request, true);
        if (repository.update(organization) != 1) throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                "Organization was changed by another user; reload and retry");
        organization.setDataVersion(organization.getDataVersion() + 1);
        historyService.record(organizationId, "HOST_ORGANIZATION", organizationId, "ADMIN_UPDATED",
                organization, List.of("PROFILE"), actorUserId, "ADMIN", null);
        return toResponse(organization);
    }

    @Override
    @Transactional
    public HostOrganizationResponse adminDisable(UUID actorUserId, UUID organizationId, String reason, Long expectedVersion) {
        HostOrganization organization = findRequired(organizationId);
        organization.setOperationalStatus(OrganizationOperationalStatus.DISABLED.name()); organization.setDataVersion(requiredVersion(expectedVersion)); organization.setUpdatedAt(LocalDateTime.now());
        if (repository.update(organization) != 1) throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                "Organization was changed by another user; reload and retry");
        organization.setDataVersion(organization.getDataVersion() + 1);
        historyService.record(organizationId, "HOST_ORGANIZATION", organizationId, "ADMIN_DISABLED",
                organization, List.of("operationalStatus"), actorUserId, "ADMIN", blankToNull(reason));
        return toResponse(organization);
    }

    @Override public List<HostOrganizationResponse> listMine(UUID actorUserId) {
        // Membership alone is not permission to discover an organisation.  This
        // also honours an expired/explicitly restricted membership consistently
        // with every subsequent partner endpoint.
        return repository.findForUser(actorUserId).stream()
                .filter(organization -> canReadOrganization(organization.getId(), actorUserId))
                .map(HostOrganizationServiceImpl::toResponse)
                .toList();
    }

    private boolean canReadOrganization(UUID organizationId, UUID actorUserId) {
        try {
            authorizationService.requireOrganization(organizationId, actorUserId);
            return true;
        } catch (BusinessException ignored) {
            return false;
        }
    }

    @Override public HostOrganizationResponse getMine(UUID actorUserId, UUID organizationId) {
        return toResponse(authorizationService.requireOrganization(organizationId, actorUserId));
    }

    @Override
    public PartnerAccessResponse getMyAccess(UUID actorUserId, UUID organizationId) {
        HostOrganization organization = authorizationService.requireOrganization(organizationId, actorUserId);
        boolean owner = organization.getOwnerUserId().equals(actorUserId);
        List<String> permissions = PARTNER_PORTAL_PERMISSIONS.stream().sorted()
                .filter(permission -> authorizationService.hasPermission(organizationId, actorUserId, permission)).toList();
        return PartnerAccessResponse.builder().organizationId(organizationId).organizationOwner(owner)
                .permissions(permissions).build();
    }

    @Override
    @Transactional
    public HostOrganizationResponse update(UUID actorUserId, UUID organizationId, UpdateHostOrganizationRequest request) {
        HostOrganization organization = authorizationService.requirePermission(organizationId, actorUserId, "ORGANIZATION_WRITE");
        applyOrganizationUpdate(organization, request, false);
        long expected = organization.getDataVersion();
        if (repository.update(organization) != 1) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "Organization was changed by another user; reload and retry");
        }
        organization.setDataVersion(expected + 1);
        historyService.record(organizationId, "HOST_ORGANIZATION", organizationId, "UPDATED",
                organization, List.of("PROFILE"), actorUserId, "USER", null);
        return toResponse(organization);
    }

    @Override public List<OrganizationMemberResponse> listMembers(UUID actorUserId, UUID organizationId) {
        authorizationService.requirePermission(organizationId, actorUserId, "MEMBER_MANAGE");
        return repository.findMembers(organizationId).stream().map(this::toMemberResponse).toList();
    }

    @Override
    @Transactional
    public OrganizationMemberResponse upsertMember(UUID actorUserId, UUID organizationId, UpsertOrganizationMemberRequest request) {
        return saveMember(actorUserId, organizationId, request, false);
    }

    @Override
    @Transactional
    public void updateMemberStatus(UUID actorUserId, UUID organizationId, UUID memberUserId, OrganizationMemberStatus status) {
        updateMemberStatusCore(actorUserId, organizationId, memberUserId, status, false);
    }

    @Override
    public List<OrganizationMemberScopeResponse> listMemberScopes(UUID actorUserId,UUID organizationId,UUID memberUserId) {
        authorizationService.requirePermission(organizationId,actorUserId,"MEMBER_MANAGE");
        OrganizationMember member=memberRequired(organizationId,memberUserId);
        return repository.findMemberScopes(member.getId(),null).stream().map(this::toScopeResponse).toList();
    }

    @Override
    @Transactional
    public OrganizationMemberScopeResponse upsertMemberScope(UUID actorUserId,UUID organizationId,UUID memberUserId,
                                                              UUID scopeId,UpsertOrganizationMemberScopeRequest request) {
        return saveMemberScope(actorUserId, organizationId, memberUserId, scopeId, request, false);
    }

    @Override
    @Transactional
    public void deleteMemberScope(UUID actorUserId,UUID organizationId,UUID memberUserId,UUID scopeId) {
        deleteMemberScopeCore(actorUserId, organizationId, memberUserId, scopeId, false);
    }

    @Override
    @Transactional
    public HostOrganizationResponse updateBilling(UUID actorUserId, UUID organizationId, UpdatePartnerBillingRequest request) {
        HostOrganization organization = authorizationService.requirePermission(organizationId, actorUserId, "ORGANIZATION_WRITE");
        String billingEmail = blankToNull(request.getBillingEmail());
        String billingDetails = request.getBillingDetails() == null ? null : JsonUtils.toJson(request.getBillingDetails());
        // A billing-only update: the commission rate is an operator decision and is never writable here.
        if (repository.updateBillingProfile(organizationId, organization.getDataVersion(), null, billingEmail,
                true, billingDetails, LocalDateTime.now()) != 1) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "Organization was changed by another user; reload and retry");
        }
        organization.setBillingEmail(billingEmail);
        if (billingDetails != null) organization.setBillingDetails(billingDetails);
        organization.setDataVersion(organization.getDataVersion() + 1);
        historyService.record(organizationId, "HOST_ORGANIZATION", organizationId, "BILLING_UPDATED",
                organization, List.of("billingEmail", "billingDetails"), actorUserId, "USER", null);
        return toResponse(organization);
    }

    @Override
    @Transactional
    public HostOrganizationResponse adminUpdateCommission(UUID actorUserId, UUID organizationId,
            UpdatePartnerCommissionRequest request) {
        HostOrganization organization = findRequired(organizationId);
        BigDecimal percent = request.getCommissionPercent();
        if (percent == null || percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(new BigDecimal("50")) > 0) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "commissionPercent must be between 0 and 50");
        }
        long expected = requiredVersion(request.getExpectedVersion());
        if (repository.updateBillingProfile(organizationId, expected, percent, null, false, null,
                LocalDateTime.now()) != 1) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "Organization was changed; reload and retry");
        }
        organization.setCommissionPercent(percent);
        organization.setDataVersion(expected + 1);
        historyService.record(organizationId, "HOST_ORGANIZATION", organizationId, "COMMISSION_CHANGED",
                organization, List.of("commissionPercent"), actorUserId, "ADMIN", null);
        return toResponse(organization);
    }

    @Override public List<HostOrganizationResponse> adminList(String query, List<String> status, List<String> organizationType, List<String> verificationStatus, String sort, boolean descending, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        return repository.findForAdmin(blankToNull(query), status, organizationType, verificationStatus, sort, descending, safeSize, safePage * safeSize)
                .stream().map(HostOrganizationServiceImpl::toResponse).toList();
    }

    @Override public HostOrganizationResponse adminGet(UUID organizationId) {
        return toResponse(findRequired(organizationId));
    }

    @Override public List<OrganizationMemberResponse> adminListMembers(UUID organizationId){
        findRequired(organizationId);
        return repository.findMembers(organizationId).stream().map(this::toMemberResponse).toList();
    }

    @Override public List<OrganizationMemberScopeResponse> adminListMemberScopes(UUID organizationId,UUID memberUserId){
        findRequired(organizationId);
        OrganizationMember member=memberRequired(organizationId,memberUserId);
        return repository.findMemberScopes(member.getId(),null).stream().map(this::toScopeResponse).toList();
    }

    @Override
    @Transactional
    public HostOrganizationResponse adminUpdateStatus(UUID actorUserId, UUID organizationId,
            OrganizationOperationalStatus operationalStatus, OrganizationVerificationStatus verificationStatus, Long expectedVersion) {
        HostOrganization organization = findRequired(organizationId);
        if (operationalStatus != null) organization.setOperationalStatus(operationalStatus.name());
        if (verificationStatus != null) organization.setVerificationStatus(verificationStatus.name());
        organization.setDataVersion(requiredVersion(expectedVersion));
        organization.setUpdatedAt(LocalDateTime.now());
        if (repository.update(organization) != 1) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "Organization was changed; reload and retry");
        }
        organization.setDataVersion(organization.getDataVersion() + 1);
        historyService.record(organizationId, "HOST_ORGANIZATION", organizationId, "ADMIN_STATUS_CHANGED",
                organization, List.of("operationalStatus", "verificationStatus"), actorUserId, "ADMIN", null);
        return toResponse(organization);
    }

    @Override
    @Transactional
    public OrganizationMemberResponse adminUpsertMember(UUID actorUserId, UUID organizationId, UpsertOrganizationMemberRequest request) {
        return saveMember(actorUserId, organizationId, request, true);
    }

    @Override
    @Transactional
    public PartnerMemberProvisionResponse provisionMember(UUID actorUserId, UUID organizationId, ProvisionPartnerMemberRequest request) {
        authorizationService.requirePermission(organizationId, actorUserId, "MEMBER_MANAGE");
        return provisionAndAttachMember(actorUserId, organizationId, request, false);
    }

    @Override
    @Transactional
    public PartnerMemberProvisionResponse adminProvisionMember(UUID actorUserId, UUID organizationId, ProvisionPartnerMemberRequest request) {
        findRequired(organizationId);
        return provisionAndAttachMember(actorUserId, organizationId, request, true);
    }

    @Override @Transactional public void adminUpdateMemberStatus(UUID actorUserId, UUID organizationId, UUID memberUserId, OrganizationMemberStatus status) {
        updateMemberStatusCore(actorUserId, organizationId, memberUserId, status, true);
    }

    @Override @Transactional public OrganizationMemberScopeResponse adminUpsertMemberScope(UUID actorUserId, UUID organizationId,
            UUID memberUserId, UUID scopeId, UpsertOrganizationMemberScopeRequest request) {
        return saveMemberScope(actorUserId, organizationId, memberUserId, scopeId, request, true);
    }

    @Override @Transactional public void adminDeleteMemberScope(UUID actorUserId, UUID organizationId, UUID memberUserId, UUID scopeId) {
        deleteMemberScopeCore(actorUserId, organizationId, memberUserId, scopeId, true);
    }

    private HostOrganization buildOrganization(UUID ownerUserId, CreateHostOrganizationRequest request) {
        validateTimezoneAndCurrency(request.getTimezone(), request.getDefaultCurrency());
        LocalDateTime now = LocalDateTime.now();
        return HostOrganization.builder().id(UUID.randomUUID()).ownerUserId(ownerUserId)
                .legalName(request.getLegalName().trim()).displayName(request.getDisplayName().trim())
                .organizationType(enumNameOrDefault(request.getOrganizationType(), OrganizationType.BUSINESS))
                .verificationStatus(OrganizationVerificationStatus.UNVERIFIED.name())
                .operationalStatus(OrganizationOperationalStatus.ENABLED.name())
                .defaultCurrency(valueOrDefault(request.getDefaultCurrency(), "VND").toUpperCase())
                .timezone(request.getTimezone()).contactEmail(blankToNull(request.getContactEmail()))
                .contactPhone(blankToNull(request.getContactPhone())).settings(toJsonMap(request.getSettings()))
                .dataVersion(1L).createdAt(now).updatedAt(now).build();
    }

    private void applyOrganizationUpdate(HostOrganization organization, UpdateHostOrganizationRequest request,
            boolean adminCanChangeOperationalStatus) {
        validateTimezoneAndCurrency(request.getTimezone(), request.getDefaultCurrency());
        if (!adminCanChangeOperationalStatus && request.getOperationalStatus() != null
                && !request.getOperationalStatus().name().equals(organization.getOperationalStatus())) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "Only an admin can change organization operational status");
        }
        long expected = requiredVersion(request.getExpectedVersion());
        organization.setLegalName(request.getLegalName().trim()); organization.setDisplayName(request.getDisplayName().trim());
        organization.setOrganizationType(enumNameOrDefault(request.getOrganizationType(), organization.getOrganizationType()));
        if (adminCanChangeOperationalStatus) {
            organization.setOperationalStatus(enumNameOrDefault(request.getOperationalStatus(), organization.getOperationalStatus()));
        }
        organization.setDefaultCurrency(valueOrDefault(request.getDefaultCurrency(), organization.getDefaultCurrency()).toUpperCase());
        organization.setTimezone(request.getTimezone()); organization.setContactEmail(blankToNull(request.getContactEmail()));
        organization.setContactPhone(blankToNull(request.getContactPhone())); organization.setSettings(toJsonMap(request.getSettings()));
        organization.setDataVersion(expected); organization.setUpdatedAt(LocalDateTime.now());
    }

    private OrganizationMemberResponse saveMember(UUID actorUserId, UUID organizationId,
            UpsertOrganizationMemberRequest request, boolean admin) {
        HostOrganization organization = admin ? findRequired(organizationId)
                : authorizationService.requirePermission(organizationId, actorUserId, "MEMBER_MANAGE");
        if (organization.getOwnerUserId().equals(request.getUserId()))
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Organization owner cannot be managed as an employee");
        userRepository.findById(request.getUserId()).orElseThrow(() -> new BusinessException(ErrorConstant.USER_NOT_FOUND));
        if (request.getValidFrom() != null && request.getValidUntil() != null && !request.getValidUntil().isAfter(request.getValidFrom()))
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "validUntil must be after validFrom");
        LocalDateTime now = LocalDateTime.now(); OrganizationMember existing = repository.findMember(organizationId, request.getUserId()).orElse(null);
        validateMemberGrant(actorUserId, organization, existing, request, admin);
        OrganizationMember member = OrganizationMember.builder().id(existing == null ? UUID.randomUUID() : existing.getId())
                .organizationId(organizationId).userId(request.getUserId()).roleCode(request.getRoleCode().name())
                .memberStatus(enumNameOrDefault(request.getMemberStatus(), OrganizationMemberStatus.ACTIVE)).permissions(toJsonList(request.getPermissions()))
                .validFrom(request.getValidFrom()).validUntil(request.getValidUntil()).invitedBy(actorUserId)
                .createdAt(existing == null ? now : existing.getCreatedAt()).updatedAt(now).build();
        repository.upsertMember(member); OrganizationMember saved = repository.findMember(organizationId, request.getUserId()).orElse(member);
        historyService.record(organizationId, "ORGANIZATION_MEMBER", saved.getId(), existing == null ? "CREATED" : "UPDATED",
                saved, List.of("ACCESS"), actorUserId, admin ? "ADMIN" : "USER", null);
        return toMemberResponse(saved);
    }

    private PartnerMemberProvisionResponse provisionAndAttachMember(UUID actorUserId, UUID organizationId,
            ProvisionPartnerMemberRequest request, boolean admin) {
        UserAccountService.ProvisionedAccount account = userAccountService.provision(request.getUsername(), request.getEmail(),
                request.getFullName(), request.getTemporaryPassword());
        UpsertOrganizationMemberRequest member = new UpsertOrganizationMemberRequest(); member.setUserId(account.user().getId());
        member.setRoleCode(request.getRoleCode()); member.setMemberStatus(OrganizationMemberStatus.ACTIVE); member.setPermissions(request.getPermissions());
        OrganizationMemberResponse saved = saveMember(actorUserId, organizationId, member, admin);
        return PartnerMemberProvisionResponse.builder().member(saved).username(account.user().getUsername())
                .temporaryPassword(account.temporaryPassword()).mustChangePassword(true).build();
    }

    private void updateMemberStatusCore(UUID actorUserId, UUID organizationId, UUID memberUserId, OrganizationMemberStatus status, boolean admin) {
        HostOrganization organization = admin ? findRequired(organizationId)
                : authorizationService.requirePermission(organizationId, actorUserId, "MEMBER_MANAGE");
        if (organization.getOwnerUserId().equals(memberUserId)) throw new BusinessException(ErrorConstant.BAD_REQUEST, "Organization owner cannot be suspended");
        OrganizationMember current = memberRequired(organizationId, memberUserId);
        if (!admin && com.ds.goroute.type.PartnerRole.PARTNER_ADMIN.name().equals(current.getRoleCode())
                && !organization.getOwnerUserId().equals(actorUserId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "Only the organization owner can change a full-access partner admin");
        }
        if (!canManuallySetMemberStatus(status))
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Invalid member status");
        if (repository.updateMemberStatus(organizationId, memberUserId, status.name(), LocalDateTime.now()) != 1)
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Organization member not found");
        OrganizationMember saved = repository.findMember(organizationId, memberUserId).orElseThrow();
        historyService.record(organizationId, "ORGANIZATION_MEMBER", saved.getId(), "STATUS_CHANGED", saved,
                List.of("memberStatus"), actorUserId, admin ? "ADMIN" : "USER", null);
    }

    private OrganizationMemberScopeResponse saveMemberScope(UUID actorUserId, UUID organizationId, UUID memberUserId,
            UUID scopeId, UpsertOrganizationMemberScopeRequest request, boolean admin) {
        if (admin) findRequired(organizationId); else authorizationService.requirePermission(organizationId, actorUserId, "MEMBER_MANAGE");
        OrganizationMember member = memberRequired(organizationId, memberUserId);
        validateScopeGrant(actorUserId, organizationId, member, request, admin);
        if(request.getValidFrom()!=null&&request.getValidUntil()!=null&&!request.getValidUntil().isAfter(request.getValidFrom()))
            throw new BusinessException(ErrorConstant.BAD_REQUEST,"validUntil must be after validFrom");
        validateScopedResource(organizationId,request.getResourceType().name(),request.getResourceId());
        OrganizationMemberScope existing=scopeId==null?null:repository.findMemberScope(scopeId)
                .filter(scope->scope.getMembershipId().equals(member.getId()))
                .orElseThrow(()->new BusinessException(ErrorConstant.NOT_FOUND,"Member scope not found"));
        OrganizationMemberScope scope=OrganizationMemberScope.builder().id(existing==null?UUID.randomUUID():existing.getId())
                .membershipId(member.getId()).resourceType(request.getResourceType().name()).resourceId(request.getResourceId())
                .roleCode(request.getRoleCode() == null ? null : request.getRoleCode().name())
                .accessEffect(enumNameOrDefault(request.getAccessEffect(), com.ds.goroute.type.ScopeAccessEffect.ALLOW))
                .permissions(toJsonList(request.getPermissions())).validFrom(request.getValidFrom()).validUntil(request.getValidUntil())
                .createdAt(existing==null?LocalDateTime.now():existing.getCreatedAt()).build();
        try { if(existing==null)repository.insertMemberScope(scope);else repository.updateMemberScope(scope); }
        catch(DataIntegrityViolationException ex){throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,"A scope already exists for this resource");}
        historyService.record(organizationId,"ORGANIZATION_MEMBER_SCOPE",scope.getId(),existing==null?"CREATED":"UPDATED",scope,
                List.of("RESOURCE_ACCESS"),actorUserId,admin?"ADMIN":"USER",null); return toScopeResponse(scope);
    }

    private void deleteMemberScopeCore(UUID actorUserId, UUID organizationId, UUID memberUserId, UUID scopeId, boolean admin) {
        if (admin) findRequired(organizationId); else authorizationService.requirePermission(organizationId, actorUserId, "MEMBER_MANAGE");
        OrganizationMember member=memberRequired(organizationId,memberUserId); OrganizationMemberScope scope=repository.findMemberScope(scopeId)
                .filter(value->value.getMembershipId().equals(member.getId()))
                .orElseThrow(()->new BusinessException(ErrorConstant.NOT_FOUND,"Member scope not found"));
        HostOrganization organization = findRequired(organizationId);
        if (!admin && com.ds.goroute.type.PartnerRole.PARTNER_ADMIN.name().equals(member.getRoleCode())
                && !organization.getOwnerUserId().equals(actorUserId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "Only the organization owner can remove a full-access partner scope");
        }
        repository.deleteMemberScope(scopeId,member.getId()); historyService.record(organizationId,"ORGANIZATION_MEMBER_SCOPE",scopeId,
                "DELETED",scope,List.of("RESOURCE_ACCESS"),actorUserId,admin?"ADMIN":"USER",null);
    }

    private HostOrganization findRequired(UUID id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Partner organization not found"));
    }

    private long requiredVersion(Long value) {
        if (value == null || value < 1) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "expectedVersion is required for an update");
        }
        return value;
    }

    private void validateTimezoneAndCurrency(String timezone, String currency) {
        try { ZoneId.of(timezone); } catch (Exception ex) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Invalid IANA timezone");
        }
        try { Currency.getInstance(currency.toUpperCase()); } catch (Exception ex) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Invalid ISO-4217 currency");
        }
    }

    static HostOrganizationResponse toResponse(HostOrganization value) {
        Map<String, Object> settings = value.getSettings() == null ? Collections.emptyMap()
                : JsonUtils.fromJson(value.getSettings(), new TypeReference<Map<String, Object>>() {});
        Map<String, Object> billingDetails = value.getBillingDetails() == null ? Collections.emptyMap()
                : JsonUtils.fromJson(value.getBillingDetails(), new TypeReference<Map<String, Object>>() {});
        return HostOrganizationResponse.builder().id(value.getId()).ownerUserId(value.getOwnerUserId())
                .legalName(value.getLegalName()).displayName(value.getDisplayName())
                .organizationType(value.getOrganizationType()).verificationStatus(value.getVerificationStatus())
                .operationalStatus(value.getOperationalStatus())
                .verificationSubmittedAt(value.getVerificationSubmittedAt()).verificationReason(value.getVerificationReason())
                .verificationDecidedAt(value.getVerificationDecidedAt()).defaultCurrency(value.getDefaultCurrency())
                .timezone(value.getTimezone()).contactEmail(value.getContactEmail()).contactPhone(value.getContactPhone())
                .settings(settings == null ? Collections.emptyMap() : settings)
                .commissionPercent(value.getCommissionPercent()).billingEmail(value.getBillingEmail())
                .billingDetails(billingDetails == null ? Collections.emptyMap() : billingDetails)
                .dataVersion(value.getDataVersion())
                .createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt()).build();
    }

    private OrganizationMemberResponse toMemberResponse(OrganizationMember value) {
        List<String> permissions = value.getPermissions() == null ? List.of()
                : JsonUtils.fromJson(value.getPermissions(), new TypeReference<List<String>>() {});
        return OrganizationMemberResponse.builder().id(value.getId()).organizationId(value.getOrganizationId())
                .userId(value.getUserId()).userName(value.getUserName()).userEmail(value.getUserEmail())
                .roleCode(value.getRoleCode()).memberStatus(value.getMemberStatus())
                .permissions(permissions == null ? List.of() : permissions).validFrom(value.getValidFrom())
                .validUntil(value.getValidUntil()).createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt()).build();
    }

    private OrganizationMemberScopeResponse toScopeResponse(OrganizationMemberScope value){
        List<String> permissions=value.getPermissions()==null?List.of():JsonUtils.fromJson(value.getPermissions(),new TypeReference<List<String>>(){});
        return OrganizationMemberScopeResponse.builder().id(value.getId()).membershipId(value.getMembershipId())
                .resourceType(value.getResourceType()).resourceId(value.getResourceId())
                .roleCode(value.getRoleCode()).accessEffect(value.getAccessEffect())
                .permissions(permissions==null?List.of():permissions).validFrom(value.getValidFrom()).validUntil(value.getValidUntil())
                .createdAt(value.getCreatedAt()).build();
    }

    private OrganizationMember memberRequired(UUID organizationId,UUID userId){
        return repository.findMember(organizationId,userId)
                .orElseThrow(()->new BusinessException(ErrorConstant.NOT_FOUND,"Organization member not found"));
    }

    private void validateScopedResource(UUID organizationId,String type,UUID resourceId){
        if(resourceId==null)return;
        if("HOTEL".equals(type)){
            var hotel=hotelRepository.findHotel(resourceId).orElseThrow(()->new BusinessException(ErrorConstant.NOT_FOUND,"Hotel not found"));
            if(!hotel.getOrganizationId().equals(organizationId))throw new BusinessException(ErrorConstant.BAD_REQUEST,"Hotel is outside organization");
        }else if("ACTIVITY".equals(type)){
            var activity=activityRepository.findProduct(resourceId).orElseThrow(()->new BusinessException(ErrorConstant.NOT_FOUND,"Activity not found"));
            if(!activity.getOrganizationId().equals(organizationId))throw new BusinessException(ErrorConstant.BAD_REQUEST,"Activity is outside organization");
        }
    }

    private void validateMemberGrant(UUID actorUserId, HostOrganization organization, OrganizationMember existing,
            UpsertOrganizationMemberRequest request, boolean admin) {
        validatePermissionNames(request.getPermissions());
        if (admin || organization.getOwnerUserId().equals(actorUserId)) return;
        if (request.getRoleCode() == com.ds.goroute.type.PartnerRole.PARTNER_ADMIN
                || existing != null && com.ds.goroute.type.PartnerRole.PARTNER_ADMIN.name().equals(existing.getRoleCode())) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "Only the organization owner can grant or change a full-access partner admin");
        }
        if (request.getPermissions() != null && request.getPermissions().contains("MEMBER_MANAGE")) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "Only the organization owner can delegate member management");
        }
    }

    private void validateScopeGrant(UUID actorUserId, UUID organizationId, OrganizationMember member,
            UpsertOrganizationMemberScopeRequest request, boolean admin) {
        validatePermissionNames(request.getPermissions());
        HostOrganization organization = findRequired(organizationId);
        if (admin || organization.getOwnerUserId().equals(actorUserId)) return;
        if (request.getRoleCode() == com.ds.goroute.type.PartnerRole.PARTNER_ADMIN
                || com.ds.goroute.type.PartnerRole.PARTNER_ADMIN.name().equals(member.getRoleCode())) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "Only the organization owner can manage full-access partner scopes");
        }
        if (request.getPermissions() != null && request.getPermissions().contains("MEMBER_MANAGE")) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "Only the organization owner can delegate member management");
        }
    }

    private void validatePermissionNames(List<String> permissions) {
        if (permissions == null) return;
        for (String permission : permissions) {
            if (permission == null || !ASSIGNABLE_PERMISSIONS.contains(permission)) {
                throw new BusinessException(ErrorConstant.BAD_REQUEST, "Unsupported partner permission");
            }
        }
    }

    private String toJsonMap(Object value) { return value == null ? "{}" : JsonUtils.toJson(value); }
    private String toJsonList(Object value) { return value == null ? "[]" : JsonUtils.toJson(value); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String enumNameOrDefault(Enum<?> value, Enum<?> fallback) { return value == null ? fallback.name() : value.name(); }
    private String enumNameOrDefault(Enum<?> value, String fallback) { return value == null ? fallback : value.name(); }
    private boolean canManuallySetMemberStatus(OrganizationMemberStatus status) {
        return status == OrganizationMemberStatus.ACTIVE || status == OrganizationMemberStatus.SUSPENDED
                || status == OrganizationMemberStatus.ACCESS_EXPIRED || status == OrganizationMemberStatus.DEACTIVATED;
    }
    private String valueOrDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
