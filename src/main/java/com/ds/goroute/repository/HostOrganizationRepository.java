package com.ds.goroute.repository;

import com.ds.goroute.entity.HostOrganization;
import com.ds.goroute.entity.OrganizationMember;
import com.ds.goroute.entity.OrganizationMemberScope;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HostOrganizationRepository {
    int insert(HostOrganization organization);
    int update(HostOrganization organization);
    Optional<HostOrganization> findById(UUID id);
    List<HostOrganization> findForUser(UUID userId);
    List<HostOrganization> findForAdmin(String query, String status, int limit, int offset);
    Optional<OrganizationMember> findMember(UUID organizationId, UUID userId);
    List<OrganizationMember> findMembers(UUID organizationId);
    int upsertMember(OrganizationMember member);
    int updateMemberStatus(UUID organizationId, UUID userId, String status, LocalDateTime updatedAt);
    List<OrganizationMemberScope> findMemberScopes(UUID membershipId, String resourceType);
    Optional<OrganizationMemberScope> findMemberScope(UUID id);
    int insertMemberScope(OrganizationMemberScope scope);
    int updateMemberScope(OrganizationMemberScope scope);
    int deleteMemberScope(UUID id, UUID membershipId);
}
