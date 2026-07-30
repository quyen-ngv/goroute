package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.HostOrganization;
import com.ds.goroute.entity.OrganizationMember;
import com.ds.goroute.entity.OrganizationMemberScope;
import com.ds.goroute.mapper.HostOrganizationMapper;
import com.ds.goroute.repository.HostOrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class HostOrganizationRepositoryImpl implements HostOrganizationRepository {
    private final HostOrganizationMapper mapper;

    @Override public int insert(HostOrganization organization) { return mapper.insertOrganization(organization); }
    @Override public int update(HostOrganization organization) { return mapper.updateOrganization(organization); }
    @Override public Optional<HostOrganization> findById(UUID id) { return Optional.ofNullable(mapper.findOrganizationById(id)); }
    @Override public List<HostOrganization> findForUser(UUID userId) { return mapper.findOrganizationsForUser(userId); }
    @Override public List<HostOrganization> findForAdmin(String query, String status, int limit, int offset) {
        return mapper.findOrganizationsAdmin(query, status, limit, offset);
    }
    @Override public Optional<OrganizationMember> findMember(UUID organizationId, UUID userId) {
        return Optional.ofNullable(mapper.findMember(organizationId, userId));
    }
    @Override public List<OrganizationMember> findMembers(UUID organizationId) { return mapper.findMembers(organizationId); }
    @Override public int upsertMember(OrganizationMember member) { return mapper.upsertMember(member); }
    @Override public int updateMemberStatus(UUID organizationId, UUID userId, String status, LocalDateTime updatedAt) {
        return mapper.updateMemberStatus(organizationId, userId, status, updatedAt);
    }
    @Override public List<OrganizationMemberScope> findMemberScopes(UUID membershipId,String resourceType){return mapper.findMemberScopes(membershipId,resourceType);}
    @Override public Optional<OrganizationMemberScope> findMemberScope(UUID id){return Optional.ofNullable(mapper.findMemberScope(id));}
    @Override public int insertMemberScope(OrganizationMemberScope scope){return mapper.insertMemberScope(scope);}
    @Override public int updateMemberScope(OrganizationMemberScope scope){return mapper.updateMemberScope(scope);}
    @Override public int deleteMemberScope(UUID id,UUID membershipId){return mapper.deleteMemberScope(id,membershipId);}
}
