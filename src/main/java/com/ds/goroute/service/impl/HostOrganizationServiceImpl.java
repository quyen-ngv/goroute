package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateHostOrganizationRequest;
import com.ds.goroute.dto.request.UpdateHostOrganizationRequest;
import com.ds.goroute.dto.request.UpsertOrganizationMemberRequest;
import com.ds.goroute.dto.request.UpsertOrganizationMemberScopeRequest;
import com.ds.goroute.dto.request.AdminProvisionPartnerRequest;
import com.ds.goroute.dto.request.ProvisionPartnerMemberRequest;
import com.ds.goroute.dto.response.HostOrganizationResponse;
import com.ds.goroute.dto.response.OrganizationMemberResponse;
import com.ds.goroute.dto.response.OrganizationMemberScopeResponse;
import com.ds.goroute.dto.response.PartnerProvisionResponse;
import com.ds.goroute.dto.response.PartnerMemberProvisionResponse;
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
import com.ds.goroute.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HostOrganizationServiceImpl implements HostOrganizationService {
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
                .organizationType(valueOrDefault(request.getOrganizationType(), "BUSINESS"))
                .verificationStatus("UNVERIFIED").operationalStatus("ENABLED")
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
        applyOrganizationUpdate(organization, request);
        if (repository.update(organization) != 1) throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                "Organization was changed by another user; reload and retry");
        organization.setDataVersion(organization.getDataVersion() + 1);
        historyService.record(organizationId, "HOST_ORGANIZATION", organizationId, "ADMIN_UPDATED",
                organization, List.of("PROFILE"), actorUserId, "ADMIN", null);
        return toResponse(organization);
    }

    @Override
    @Transactional
    public HostOrganizationResponse adminDisable(UUID actorUserId, UUID organizationId, String reason) {
        HostOrganization organization = findRequired(organizationId);
        organization.setOperationalStatus("DISABLED"); organization.setUpdatedAt(LocalDateTime.now());
        if (repository.update(organization) != 1) throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                "Organization was changed by another user; reload and retry");
        organization.setDataVersion(organization.getDataVersion() + 1);
        historyService.record(organizationId, "HOST_ORGANIZATION", organizationId, "ADMIN_DISABLED",
                organization, List.of("operationalStatus"), actorUserId, "ADMIN", blankToNull(reason));
        return toResponse(organization);
    }

    @Override public List<HostOrganizationResponse> listMine(UUID actorUserId) {
        return repository.findForUser(actorUserId).stream().map(this::toResponse).toList();
    }

    @Override public HostOrganizationResponse getMine(UUID actorUserId, UUID organizationId) {
        return toResponse(authorizationService.requireOrganization(organizationId, actorUserId));
    }

    @Override
    @Transactional
    public HostOrganizationResponse update(UUID actorUserId, UUID organizationId, UpdateHostOrganizationRequest request) {
        HostOrganization organization = authorizationService.requirePermission(organizationId, actorUserId, "ORGANIZATION_WRITE");
        applyOrganizationUpdate(organization, request);
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
        HostOrganization organization = authorizationService.requirePermission(organizationId, actorUserId, "MEMBER_MANAGE");
        if (organization.getOwnerUserId().equals(request.getUserId())) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Organization owner cannot be managed as an employee");
        }
        userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.USER_NOT_FOUND));
        if (request.getValidFrom() != null && request.getValidUntil() != null
                && !request.getValidUntil().isAfter(request.getValidFrom())) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "validUntil must be after validFrom");
        }
        LocalDateTime now = LocalDateTime.now();
        OrganizationMember existing = repository.findMember(organizationId, request.getUserId()).orElse(null);
        OrganizationMember member = OrganizationMember.builder()
                .id(existing == null ? UUID.randomUUID() : existing.getId())
                .organizationId(organizationId).userId(request.getUserId())
                .roleCode(request.getRoleCode()).memberStatus(valueOrDefault(request.getMemberStatus(), "ACTIVE"))
                .permissions(toJsonList(request.getPermissions())).validFrom(request.getValidFrom())
                .validUntil(request.getValidUntil()).invitedBy(actorUserId)
                .createdAt(existing == null ? now : existing.getCreatedAt()).updatedAt(now).build();
        repository.upsertMember(member);
        OrganizationMember saved = repository.findMember(organizationId, request.getUserId()).orElse(member);
        historyService.record(organizationId, "ORGANIZATION_MEMBER", saved.getId(),
                existing == null ? "CREATED" : "UPDATED", saved, List.of("ACCESS"), actorUserId, "USER", null);
        return toMemberResponse(saved);
    }

    @Override
    @Transactional
    public void updateMemberStatus(UUID actorUserId, UUID organizationId, UUID memberUserId, String status) {
        HostOrganization organization = authorizationService.requirePermission(organizationId, actorUserId, "MEMBER_MANAGE");
        if (organization.getOwnerUserId().equals(memberUserId)) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Organization owner cannot be suspended");
        }
        if (!List.of("ACTIVE", "SUSPENDED", "ACCESS_EXPIRED", "DEACTIVATED").contains(status)) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Invalid member status");
        }
        if (repository.updateMemberStatus(organizationId, memberUserId, status, LocalDateTime.now()) != 1) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Organization member not found");
        }
        OrganizationMember saved = repository.findMember(organizationId, memberUserId).orElseThrow();
        historyService.record(organizationId, "ORGANIZATION_MEMBER", saved.getId(), "STATUS_CHANGED",
                saved, List.of("memberStatus"), actorUserId, "USER", null);
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
        authorizationService.requirePermission(organizationId,actorUserId,"MEMBER_MANAGE");
        OrganizationMember member=memberRequired(organizationId,memberUserId);
        if(request.getValidFrom()!=null&&request.getValidUntil()!=null&&!request.getValidUntil().isAfter(request.getValidFrom()))
            throw new BusinessException(ErrorConstant.BAD_REQUEST,"validUntil must be after validFrom");
        validateScopedResource(organizationId,request.getResourceType(),request.getResourceId());
        OrganizationMemberScope existing=scopeId==null?null:repository.findMemberScope(scopeId)
                .filter(scope->scope.getMembershipId().equals(member.getId()))
                .orElseThrow(()->new BusinessException(ErrorConstant.NOT_FOUND,"Member scope not found"));
        OrganizationMemberScope scope=OrganizationMemberScope.builder().id(existing==null?UUID.randomUUID():existing.getId())
                .membershipId(member.getId()).resourceType(request.getResourceType()).resourceId(request.getResourceId())
                .roleCode(request.getRoleCode()).accessEffect(valueOrDefault(request.getAccessEffect(),"ALLOW"))
                .permissions(toJsonList(request.getPermissions())).validFrom(request.getValidFrom()).validUntil(request.getValidUntil())
                .createdAt(existing==null?LocalDateTime.now():existing.getCreatedAt()).build();
        try { if(existing==null)repository.insertMemberScope(scope);else repository.updateMemberScope(scope); }
        catch(DataIntegrityViolationException ex){throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,"A scope already exists for this resource");}
        historyService.record(organizationId,"ORGANIZATION_MEMBER_SCOPE",scope.getId(),existing==null?"CREATED":"UPDATED",scope,
                List.of("RESOURCE_ACCESS"),actorUserId,"USER",null);
        return toScopeResponse(scope);
    }

    @Override
    @Transactional
    public void deleteMemberScope(UUID actorUserId,UUID organizationId,UUID memberUserId,UUID scopeId) {
        authorizationService.requirePermission(organizationId,actorUserId,"MEMBER_MANAGE");
        OrganizationMember member=memberRequired(organizationId,memberUserId);
        OrganizationMemberScope scope=repository.findMemberScope(scopeId)
                .filter(value->value.getMembershipId().equals(member.getId()))
                .orElseThrow(()->new BusinessException(ErrorConstant.NOT_FOUND,"Member scope not found"));
        repository.deleteMemberScope(scopeId,member.getId());
        historyService.record(organizationId,"ORGANIZATION_MEMBER_SCOPE",scopeId,"DELETED",scope,
                List.of("RESOURCE_ACCESS"),actorUserId,"USER",null);
    }

    @Override public List<HostOrganizationResponse> adminList(String query, String status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        return repository.findForAdmin(blankToNull(query), blankToNull(status), safeSize, safePage * safeSize)
                .stream().map(this::toResponse).toList();
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
    public HostOrganizationResponse adminUpdateStatus(UUID organizationId, String operationalStatus, String verificationStatus) {
        HostOrganization organization = findRequired(organizationId);
        if (operationalStatus != null) organization.setOperationalStatus(operationalStatus);
        if (verificationStatus != null) organization.setVerificationStatus(verificationStatus);
        organization.setUpdatedAt(LocalDateTime.now());
        if (repository.update(organization) != 1) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "Organization was changed; reload and retry");
        }
        organization.setDataVersion(organization.getDataVersion() + 1);
        historyService.record(organizationId, "HOST_ORGANIZATION", organizationId, "ADMIN_STATUS_CHANGED",
                organization, List.of("operationalStatus", "verificationStatus"), null, "ADMIN", null);
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

    @Override public void adminUpdateMemberStatus(UUID actorUserId, UUID organizationId, UUID memberUserId, String status) {
        updateMemberStatusCore(actorUserId, organizationId, memberUserId, status, true);
    }

    @Override public OrganizationMemberScopeResponse adminUpsertMemberScope(UUID actorUserId, UUID organizationId,
            UUID memberUserId, UUID scopeId, UpsertOrganizationMemberScopeRequest request) {
        return saveMemberScope(actorUserId, organizationId, memberUserId, scopeId, request, true);
    }

    @Override public void adminDeleteMemberScope(UUID actorUserId, UUID organizationId, UUID memberUserId, UUID scopeId) {
        deleteMemberScopeCore(actorUserId, organizationId, memberUserId, scopeId, true);
    }

    private HostOrganization buildOrganization(UUID ownerUserId, CreateHostOrganizationRequest request) {
        validateTimezoneAndCurrency(request.getTimezone(), request.getDefaultCurrency());
        LocalDateTime now = LocalDateTime.now();
        return HostOrganization.builder().id(UUID.randomUUID()).ownerUserId(ownerUserId)
                .legalName(request.getLegalName().trim()).displayName(request.getDisplayName().trim())
                .organizationType(valueOrDefault(request.getOrganizationType(), "BUSINESS"))
                .verificationStatus("UNVERIFIED").operationalStatus("ENABLED")
                .defaultCurrency(valueOrDefault(request.getDefaultCurrency(), "VND").toUpperCase())
                .timezone(request.getTimezone()).contactEmail(blankToNull(request.getContactEmail()))
                .contactPhone(blankToNull(request.getContactPhone())).settings(toJsonMap(request.getSettings()))
                .dataVersion(1L).createdAt(now).updatedAt(now).build();
    }

    private void applyOrganizationUpdate(HostOrganization organization, UpdateHostOrganizationRequest request) {
        validateTimezoneAndCurrency(request.getTimezone(), request.getDefaultCurrency());
        long expected = request.getExpectedVersion() == null ? organization.getDataVersion() : request.getExpectedVersion();
        organization.setLegalName(request.getLegalName().trim()); organization.setDisplayName(request.getDisplayName().trim());
        organization.setOrganizationType(valueOrDefault(request.getOrganizationType(), organization.getOrganizationType()));
        organization.setOperationalStatus(valueOrDefault(request.getOperationalStatus(), organization.getOperationalStatus()));
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
        OrganizationMember member = OrganizationMember.builder().id(existing == null ? UUID.randomUUID() : existing.getId())
                .organizationId(organizationId).userId(request.getUserId()).roleCode(request.getRoleCode())
                .memberStatus(valueOrDefault(request.getMemberStatus(), "ACTIVE")).permissions(toJsonList(request.getPermissions()))
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
        member.setRoleCode(request.getRoleCode()); member.setMemberStatus("ACTIVE"); member.setPermissions(request.getPermissions());
        OrganizationMemberResponse saved = saveMember(actorUserId, organizationId, member, admin);
        return PartnerMemberProvisionResponse.builder().member(saved).username(account.user().getUsername())
                .temporaryPassword(account.temporaryPassword()).mustChangePassword(true).build();
    }

    private void updateMemberStatusCore(UUID actorUserId, UUID organizationId, UUID memberUserId, String status, boolean admin) {
        HostOrganization organization = admin ? findRequired(organizationId)
                : authorizationService.requirePermission(organizationId, actorUserId, "MEMBER_MANAGE");
        if (organization.getOwnerUserId().equals(memberUserId)) throw new BusinessException(ErrorConstant.BAD_REQUEST, "Organization owner cannot be suspended");
        if (!List.of("ACTIVE", "SUSPENDED", "ACCESS_EXPIRED", "DEACTIVATED").contains(status))
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Invalid member status");
        if (repository.updateMemberStatus(organizationId, memberUserId, status, LocalDateTime.now()) != 1)
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Organization member not found");
        OrganizationMember saved = repository.findMember(organizationId, memberUserId).orElseThrow();
        historyService.record(organizationId, "ORGANIZATION_MEMBER", saved.getId(), "STATUS_CHANGED", saved,
                List.of("memberStatus"), actorUserId, admin ? "ADMIN" : "USER", null);
    }

    private OrganizationMemberScopeResponse saveMemberScope(UUID actorUserId, UUID organizationId, UUID memberUserId,
            UUID scopeId, UpsertOrganizationMemberScopeRequest request, boolean admin) {
        if (admin) findRequired(organizationId); else authorizationService.requirePermission(organizationId, actorUserId, "MEMBER_MANAGE");
        OrganizationMember member = memberRequired(organizationId, memberUserId);
        if(request.getValidFrom()!=null&&request.getValidUntil()!=null&&!request.getValidUntil().isAfter(request.getValidFrom()))
            throw new BusinessException(ErrorConstant.BAD_REQUEST,"validUntil must be after validFrom");
        validateScopedResource(organizationId,request.getResourceType(),request.getResourceId());
        OrganizationMemberScope existing=scopeId==null?null:repository.findMemberScope(scopeId)
                .filter(scope->scope.getMembershipId().equals(member.getId()))
                .orElseThrow(()->new BusinessException(ErrorConstant.NOT_FOUND,"Member scope not found"));
        OrganizationMemberScope scope=OrganizationMemberScope.builder().id(existing==null?UUID.randomUUID():existing.getId())
                .membershipId(member.getId()).resourceType(request.getResourceType()).resourceId(request.getResourceId())
                .roleCode(request.getRoleCode()).accessEffect(valueOrDefault(request.getAccessEffect(),"ALLOW"))
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
        repository.deleteMemberScope(scopeId,member.getId()); historyService.record(organizationId,"ORGANIZATION_MEMBER_SCOPE",scopeId,
                "DELETED",scope,List.of("RESOURCE_ACCESS"),actorUserId,admin?"ADMIN":"USER",null);
    }

    private HostOrganization findRequired(UUID id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Partner organization not found"));
    }

    private void validateTimezoneAndCurrency(String timezone, String currency) {
        try { ZoneId.of(timezone); } catch (Exception ex) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Invalid IANA timezone");
        }
        try { Currency.getInstance(currency.toUpperCase()); } catch (Exception ex) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Invalid ISO-4217 currency");
        }
    }

    private HostOrganizationResponse toResponse(HostOrganization value) {
        Map<String, Object> settings = value.getSettings() == null ? Collections.emptyMap()
                : JsonUtils.fromJson(value.getSettings(), new TypeReference<Map<String, Object>>() {});
        return HostOrganizationResponse.builder().id(value.getId()).ownerUserId(value.getOwnerUserId())
                .legalName(value.getLegalName()).displayName(value.getDisplayName())
                .organizationType(value.getOrganizationType()).verificationStatus(value.getVerificationStatus())
                .operationalStatus(value.getOperationalStatus()).defaultCurrency(value.getDefaultCurrency())
                .timezone(value.getTimezone()).contactEmail(value.getContactEmail()).contactPhone(value.getContactPhone())
                .settings(settings == null ? Collections.emptyMap() : settings).dataVersion(value.getDataVersion())
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

    private String toJsonMap(Object value) { return value == null ? "{}" : JsonUtils.toJson(value); }
    private String toJsonList(Object value) { return value == null ? "[]" : JsonUtils.toJson(value); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String valueOrDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
