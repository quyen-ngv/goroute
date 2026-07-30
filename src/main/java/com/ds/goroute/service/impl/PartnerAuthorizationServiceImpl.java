package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.entity.HostOrganization;
import com.ds.goroute.entity.OrganizationMember;
import com.ds.goroute.entity.OrganizationMemberScope;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.HostOrganizationRepository;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.service.PartnerAuthorizationService;
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
    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.ofEntries(
            Map.entry("PARTNER_ADMIN", ALL),
            Map.entry("PROPERTY_MANAGER", Set.of("ORGANIZATION_READ", "HOTEL_READ", "HOTEL_WRITE", "ROOM_WRITE", "INVENTORY_WRITE", "BOOKING_READ", "BOOKING_WRITE", "CHAT_WRITE", "REVIEW_RESPOND")),
            Map.entry("REVENUE_MANAGER", Set.of("ORGANIZATION_READ", "HOTEL_READ", "RATE_WRITE", "INVENTORY_WRITE", "BOOKING_READ", "REPORT_READ")),
            Map.entry("RESERVATION_AGENT", Set.of("ORGANIZATION_READ", "HOTEL_READ", "BOOKING_READ", "BOOKING_WRITE", "CHAT_WRITE")),
            Map.entry("FRONT_DESK", Set.of("ORGANIZATION_READ", "HOTEL_READ", "BOOKING_READ", "BOOKING_WRITE", "CHAT_WRITE")),
            Map.entry("HOUSEKEEPING", Set.of("ORGANIZATION_READ", "HOTEL_READ", "BOOKING_READ")),
            Map.entry("FINANCE", Set.of("ORGANIZATION_READ", "BOOKING_READ", "ORDER_READ", "REPORT_READ")),
            Map.entry("CONTENT_MANAGER", Set.of("ORGANIZATION_READ", "HOTEL_READ", "HOTEL_WRITE", "ROOM_WRITE", "ACTIVITY_READ", "ACTIVITY_WRITE", "REVIEW_RESPOND")),
            Map.entry("TOUR_OPERATOR", Set.of("ORGANIZATION_READ", "ACTIVITY_READ", "ACTIVITY_WRITE", "SLOT_WRITE", "ORDER_READ", "ORDER_WRITE", "CHAT_WRITE", "REVIEW_RESPOND")),
            Map.entry("GUIDE", Set.of("ORGANIZATION_READ", "ACTIVITY_READ", "ORDER_READ", "CHAT_WRITE")),
            Map.entry("TICKET_SCANNER", Set.of("ORGANIZATION_READ", "ACTIVITY_READ", "ORDER_READ", "ORDER_WRITE")),
            Map.entry("VIEWER", Set.of("ORGANIZATION_READ", "HOTEL_READ", "ACTIVITY_READ", "BOOKING_READ", "ORDER_READ", "REPORT_READ"))
    );

    private final HostOrganizationRepository repository;
    private final AdminMapper adminMapper;

    @Override
    public HostOrganization requireOrganization(UUID organizationId, UUID actorUserId) {
        HostOrganization organization = repository.findById(organizationId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Partner organization not found"));
        if (adminCan(actorUserId, "ORGANIZATION_READ")) return organization;
        if (organization.getOwnerUserId().equals(actorUserId)) {
            return organization;
        }
        OrganizationMember member = activeMember(organizationId, actorUserId);
        if (member == null) {
            throw forbidden();
        }
        return organization;
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
        boolean denied=matching.stream().filter(scope->"DENY".equals(scope.getAccessEffect()))
                .anyMatch(scope->scopeDenies(scope,permission));
        if(denied)throw forbidden();
        boolean hasAllowScopes=activeScopes.stream().anyMatch(scope->!"DENY".equals(scope.getAccessEffect()));
        boolean allowed=hasAllowScopes
                ? matching.stream().filter(scope->!"DENY".equals(scope.getAccessEffect()))
                    .anyMatch(scope->scopeAllows(scope,member,permission))
                : hasPermission(member,permission);
        if (!allowed) throw forbidden();
        return organization;
    }

    @Override
    public boolean hasResourcePermission(UUID organizationId,UUID actorUserId,String resourceType,UUID resourceId,String permission) {
        try { requireResourcePermission(organizationId,actorUserId,resourceType,resourceId,permission); return true; }
        catch (BusinessException ex) { return false; }
    }

    private OrganizationMember activeMember(UUID organizationId, UUID userId) {
        OrganizationMember member = repository.findMember(organizationId, userId).orElse(null);
        if (member == null || !"ACTIVE".equals(member.getMemberStatus())) return null;
        LocalDateTime now = LocalDateTime.now();
        if (member.getValidFrom() != null && member.getValidFrom().isAfter(now)) return null;
        if (member.getValidUntil() != null && !member.getValidUntil().isAfter(now)) return null;
        return member;
    }

    private boolean hasPermission(OrganizationMember member, String permission) {
        List<String> explicit = member.getPermissions() == null ? null
                : JsonUtils.fromJson(member.getPermissions(), new TypeReference<List<String>>() {});
        Set<String> rolePermissions=ROLE_PERMISSIONS.getOrDefault(member.getRoleCode(),Set.of());
        return rolePermissions.contains("*")||rolePermissions.contains(permission)
                ||explicit!=null&&(explicit.contains("*")||explicit.contains(permission));
    }

    private boolean scopeAllows(OrganizationMemberScope scope,OrganizationMember member,String permission) {
        List<String> permissions=scope.getPermissions()==null?List.of():JsonUtils.fromJson(scope.getPermissions(),new TypeReference<List<String>>(){});
        Set<String> rolePermissions=scope.getRoleCode()==null?Set.of():ROLE_PERMISSIONS.getOrDefault(scope.getRoleCode(),Set.of());
        boolean explicit=permissions!=null&&!permissions.isEmpty();
        if(rolePermissions.contains("*")||rolePermissions.contains(permission)
                ||explicit&&(permissions.contains("*")||permissions.contains(permission)))return true;
        return scope.getRoleCode()==null&&!explicit&&hasPermission(member,permission);
    }

    private boolean scopeDenies(OrganizationMemberScope scope,String permission){
        List<String> permissions=scope.getPermissions()==null?List.of():JsonUtils.fromJson(scope.getPermissions(),new TypeReference<List<String>>(){});
        Set<String> rolePermissions=scope.getRoleCode()==null?Set.of():ROLE_PERMISSIONS.getOrDefault(scope.getRoleCode(),Set.of());
        if(scope.getRoleCode()==null&&(permissions==null||permissions.isEmpty()))return true;
        return rolePermissions.contains("*")||rolePermissions.contains(permission)
                ||permissions!=null&&(permissions.contains("*")||permissions.contains(permission));
    }

    private boolean isActive(OrganizationMemberScope scope,LocalDateTime now){
        return (scope.getValidFrom()==null||!scope.getValidFrom().isAfter(now))
                &&(scope.getValidUntil()==null||scope.getValidUntil().isAfter(now));
    }

    private void requireOperationalAccess(HostOrganization organization, String permission) {
        boolean readOnly = permission != null && permission.endsWith("_READ");
        boolean suspended = "SUSPENDED".equals(organization.getOperationalStatus())
                || "SUSPENDED".equals(organization.getVerificationStatus());
        if (suspended && !readOnly) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "This partner organization is suspended");
        }
        if ("DISABLED".equals(organization.getOperationalStatus())
                && !readOnly && !"ORGANIZATION_WRITE".equals(permission)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "This partner organization is disabled");
        }
    }

    private BusinessException forbidden() {
        return new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You do not have permission for this partner organization");
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
