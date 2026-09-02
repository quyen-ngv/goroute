package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.entity.HostOrganization;
import com.ds.goroute.entity.OrganizationMember;
import com.ds.goroute.entity.OrganizationMemberScope;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.HostOrganizationRepository;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.type.OrganizationMemberStatus;
import com.ds.goroute.type.OrganizationOperationalStatus;
import com.ds.goroute.type.OrganizationVerificationStatus;
import com.ds.goroute.type.PartnerRole;
import com.ds.goroute.type.ScopeAccessEffect;
import com.ds.goroute.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerAuthorizationServiceImpl implements PartnerAuthorizationService {
    private static final Set<String> ALL = Set.of("*");
    private static final Map<PartnerRole, Set<String>> ROLE_PERMISSIONS = Map.ofEntries(
            Map.entry(PartnerRole.PARTNER_ADMIN, ALL),
            Map.entry(PartnerRole.PROPERTY_MANAGER, Set.of("ORGANIZATION_READ", "HOTEL_READ", "HOTEL_WRITE", "ROOM_WRITE", "INVENTORY_WRITE", "BOOKING_READ", "BOOKING_WRITE", "CHAT_WRITE", "REVIEW_RESPOND")),
            Map.entry(PartnerRole.REVENUE_MANAGER, Set.of("ORGANIZATION_READ", "HOTEL_READ", "RATE_WRITE", "INVENTORY_WRITE", "BOOKING_READ", "REPORT_READ")),
            Map.entry(PartnerRole.RESERVATION_AGENT, Set.of("ORGANIZATION_READ", "HOTEL_READ", "BOOKING_READ", "BOOKING_WRITE", "CHAT_WRITE")),
            Map.entry(PartnerRole.FRONT_DESK, Set.of("ORGANIZATION_READ", "HOTEL_READ", "BOOKING_READ", "BOOKING_WRITE", "CHAT_WRITE")),
            Map.entry(PartnerRole.HOUSEKEEPING, Set.of("ORGANIZATION_READ", "HOTEL_READ", "BOOKING_READ")),
            Map.entry(PartnerRole.FINANCE, Set.of("ORGANIZATION_READ", "BOOKING_READ", "ORDER_READ", "REPORT_READ")),
            Map.entry(PartnerRole.CONTENT_MANAGER, Set.of("ORGANIZATION_READ", "HOTEL_READ", "HOTEL_WRITE", "ROOM_WRITE", "ACTIVITY_READ", "ACTIVITY_WRITE", "REVIEW_RESPOND")),
            Map.entry(PartnerRole.TOUR_OPERATOR, Set.of("ORGANIZATION_READ", "ACTIVITY_READ", "ACTIVITY_WRITE", "SLOT_WRITE", "ORDER_READ", "ORDER_WRITE", "CHAT_WRITE", "REVIEW_RESPOND")),
            Map.entry(PartnerRole.GUIDE, Set.of("ORGANIZATION_READ", "ACTIVITY_READ", "ORDER_READ", "CHAT_WRITE")),
            Map.entry(PartnerRole.TICKET_SCANNER, Set.of("ORGANIZATION_READ", "ACTIVITY_READ", "ORDER_READ", "ORDER_WRITE")),
            Map.entry(PartnerRole.VIEWER, Set.of("ORGANIZATION_READ", "HOTEL_READ", "ACTIVITY_READ", "BOOKING_READ", "ORDER_READ", "REPORT_READ"))
    );

    private final HostOrganizationRepository repository;
    private final AdminMapper adminMapper;

    @Override
    public HostOrganization requireOrganization(UUID organizationId, UUID actorUserId) {
        return requirePermission(organizationId, actorUserId, "ORGANIZATION_READ");
    }

    @Override
    public HostOrganization requirePermission(UUID organizationId, UUID actorUserId, String permission) {
        HostOrganization organization = repository.findById(organizationId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Partner organization not found"));
        if (adminCan(actorUserId, permission)) return organization;
        requireOperationalAccess(organization, permission);
        if (organization.getOwnerUserId().equals(actorUserId)) {
            return organization;
        }
        OrganizationMember member = activeMember(organizationId, actorUserId);
        if (member == null || !hasPermission(member, permission)) {
            throw forbidden();
        }
        return organization;
    }

    @Override
    public HostOrganization requireResourcePermission(UUID organizationId,UUID actorUserId,String resourceType,
                                                      UUID resourceId,String permission) {
        HostOrganization organization=repository.findById(organizationId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Partner organization not found"));
        if(adminCan(actorUserId,permission))return organization;
        requireOperationalAccess(organization,permission);
        if (organization.getOwnerUserId().equals(actorUserId)) return organization;
        OrganizationMember member=activeMember(organizationId,actorUserId);
        if(member==null)throw forbidden();
        List<OrganizationMemberScope> scopes=repository.findMemberScopes(member.getId(),resourceType);
        if(scopes.isEmpty()){
            if(!hasPermission(member,permission))throw forbidden();
            return organization;
        }
        LocalDateTime now=LocalDateTime.now();
        List<OrganizationMemberScope> activeScopes=scopes.stream().filter(scope->isActive(scope,now)).toList();
        List<OrganizationMemberScope> matching=activeScopes.stream()
                .filter(scope->scope.getResourceId()==null||scope.getResourceId().equals(resourceId)).toList();
        boolean denied=matching.stream().filter(scope->ScopeAccessEffect.DENY.name().equals(scope.getAccessEffect()))
                .anyMatch(scope->scopeDenies(scope,permission));
        if(denied)throw forbidden();
        boolean hasAllowScopes=activeScopes.stream().anyMatch(scope->!ScopeAccessEffect.DENY.name().equals(scope.getAccessEffect()));
        boolean allowed=hasAllowScopes
                ? matching.stream().filter(scope->!ScopeAccessEffect.DENY.name().equals(scope.getAccessEffect()))
                    .anyMatch(scope->scopeAllows(scope,member,permission))
                : hasPermission(member,permission);
        if (!allowed) throw forbidden();
        return organization;
    }

    @Override
    public boolean hasPermission(UUID organizationId, UUID actorUserId, String permission) {
        try { requirePermission(organizationId, actorUserId, permission); return true; }
        catch (BusinessException ex) { return false; }
    }

    @Override
    public boolean hasResourcePermission(UUID organizationId,UUID actorUserId,String resourceType,UUID resourceId,String permission) {
        try { requireResourcePermission(organizationId,actorUserId,resourceType,resourceId,permission); return true; }
        catch (BusinessException ex) { return false; }
    }

    @Override
    public List<UUID> accessibleResourceIds(UUID organizationId, UUID actorUserId, String resourceType,
                                            List<UUID> candidateIds, String permission) {
        HostOrganization organization = repository.findById(organizationId).orElse(null);
        if (organization == null) return List.of();
        if (adminCan(actorUserId, permission) || organization.getOwnerUserId().equals(actorUserId)) return null;
        OrganizationMember member = activeMember(organizationId, actorUserId);
        if (member == null) return List.of();
        if (repository.findMemberScopes(member.getId(), resourceType).isEmpty()) {
            return hasPermission(member, permission) ? null : List.of();
        }
        return candidateIds.stream()
                .filter(id -> hasResourcePermission(organizationId, actorUserId, resourceType, id, permission))
                .toList();
    }

    @Override
    public List<UUID> notificationRecipients(UUID organizationId, String resourceType, UUID resourceId,
                                             String permission, UUID excludeUserId) {
        HostOrganization organization = repository.findById(organizationId).orElse(null);
        if (organization == null) return List.of();
        java.util.LinkedHashSet<UUID> recipients = new java.util.LinkedHashSet<>();
        if (organization.getOwnerUserId() != null) recipients.add(organization.getOwnerUserId());
        for (OrganizationMember member : repository.findMembers(organizationId)) {
            if (!OrganizationMemberStatus.ACTIVE.name().equals(member.getMemberStatus())) continue;
            if (hasResourcePermission(organizationId, member.getUserId(), resourceType, resourceId, permission)) {
                recipients.add(member.getUserId());
            }
        }
        if (excludeUserId != null) recipients.remove(excludeUserId);
        return List.copyOf(recipients);
    }

    private OrganizationMember activeMember(UUID organizationId, UUID userId) {
        OrganizationMember member = repository.findMember(organizationId, userId).orElse(null);
        if (member == null || !OrganizationMemberStatus.ACTIVE.name().equals(member.getMemberStatus())) return null;
        LocalDateTime now = LocalDateTime.now();
        if (member.getValidFrom() != null && member.getValidFrom().isAfter(now)) return null;
        if (member.getValidUntil() != null && !member.getValidUntil().isAfter(now)) return null;
        return member;
    }

    private boolean hasPermission(OrganizationMember member, String permission) {
        List<String> explicit = member.getPermissions() == null ? null
                : JsonUtils.fromJson(member.getPermissions(), new TypeReference<List<String>>() {});
        Set<String> rolePermissions=permissionsForRole(member.getRoleCode());
        return rolePermissions.contains("*")||rolePermissions.contains(permission)
                ||explicit!=null&&explicit.contains(permission);
    }

    private boolean scopeAllows(OrganizationMemberScope scope,OrganizationMember member,String permission) {
        List<String> permissions=scope.getPermissions()==null?List.of():JsonUtils.fromJson(scope.getPermissions(),new TypeReference<List<String>>(){});
        Set<String> rolePermissions=permissionsForRole(scope.getRoleCode());
        boolean explicit=permissions!=null&&!permissions.isEmpty();
        if(rolePermissions.contains("*")||rolePermissions.contains(permission)
                ||explicit&&permissions.contains(permission))return true;
        return scope.getRoleCode()==null&&!explicit&&hasPermission(member,permission);
    }

    private boolean scopeDenies(OrganizationMemberScope scope,String permission){
        List<String> permissions=scope.getPermissions()==null?List.of():JsonUtils.fromJson(scope.getPermissions(),new TypeReference<List<String>>(){});
        Set<String> rolePermissions=permissionsForRole(scope.getRoleCode());
        if(scope.getRoleCode()==null&&(permissions==null||permissions.isEmpty()))return true;
        return rolePermissions.contains("*")||rolePermissions.contains(permission)
                ||permissions!=null&&permissions.contains(permission);
    }

    private boolean isActive(OrganizationMemberScope scope,LocalDateTime now){
        return (scope.getValidFrom()==null||!scope.getValidFrom().isAfter(now))
                &&(scope.getValidUntil()==null||scope.getValidUntil().isAfter(now));
    }

    private void requireOperationalAccess(HostOrganization organization, String permission) {
        boolean readOnly = permission != null && permission.endsWith("_READ");
        boolean suspended = OrganizationOperationalStatus.SUSPENDED.name().equals(organization.getOperationalStatus())
                || OrganizationVerificationStatus.SUSPENDED.name().equals(organization.getVerificationStatus());
        if (suspended && !readOnly) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "This partner organization is suspended");
        }
        if (OrganizationOperationalStatus.DISABLED.name().equals(organization.getOperationalStatus())
                && !readOnly && !"ORGANIZATION_WRITE".equals(permission)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "This partner organization is disabled");
        }
    }

    private BusinessException forbidden() {
        return new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You do not have permission for this partner organization");
    }

    private Set<String> permissionsForRole(String roleCode) {
        if (roleCode == null) return Set.of();
        try { return ROLE_PERMISSIONS.getOrDefault(PartnerRole.valueOf(roleCode), Set.of()); }
        catch (IllegalArgumentException ignored) { return Set.of(); }
    }

    private boolean adminCan(UUID userId, String permission) {
        String resource;
        if (permission == null) return false;
        if (permission.startsWith("HOTEL_") || permission.startsWith("ROOM_") || permission.startsWith("RATE_")
                || permission.startsWith("INVENTORY_") || permission.startsWith("BOOKING_")) resource = "marketplace-hotels";
        else if (permission.startsWith("ACTIVITY_") || permission.startsWith("SLOT_") || permission.startsWith("ORDER_"))
            resource = "marketplace-activities";
        else if (permission.startsWith("CHAT_")) resource = "marketplace-conversations";
        else if (permission.startsWith("REVIEW_")) resource = "marketplace-reviews";
        else if (permission.startsWith("ORGANIZATION_") || permission.startsWith("MEMBER_")) resource = "partner-organizations";
        else return false;
        if (permission.endsWith("_READ")) return adminMapper.hasPermission(userId, resource, "get");
        return adminMapper.hasPermission(userId, resource, "update") || adminMapper.hasPermission(userId, resource, "create");
    }
}
