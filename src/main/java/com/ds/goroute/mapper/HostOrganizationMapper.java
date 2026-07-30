package com.ds.goroute.mapper;

import com.ds.goroute.entity.HostOrganization;
import com.ds.goroute.entity.OrganizationMember;
import com.ds.goroute.entity.OrganizationMemberScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface HostOrganizationMapper {
    int insertOrganization(HostOrganization organization);
    int updateOrganization(HostOrganization organization);
    HostOrganization findOrganizationById(@Param("id") UUID id);
    List<HostOrganization> findOrganizationsForUser(@Param("userId") UUID userId);
    List<HostOrganization> findOrganizationsAdmin(@Param("query") String query,
                                                  @Param("status") String status,
                                                  @Param("limit") int limit,
                                                  @Param("offset") int offset);
    OrganizationMember findMember(@Param("organizationId") UUID organizationId,
                                  @Param("userId") UUID userId);
    List<OrganizationMember> findMembers(@Param("organizationId") UUID organizationId);
    int upsertMember(OrganizationMember member);
    int updateMemberStatus(@Param("organizationId") UUID organizationId,
                           @Param("userId") UUID userId,
                           @Param("status") String status,
                           @Param("updatedAt") java.time.LocalDateTime updatedAt);
    List<OrganizationMemberScope> findMemberScopes(@Param("membershipId") UUID membershipId,
                                                   @Param("resourceType") String resourceType);
    OrganizationMemberScope findMemberScope(@Param("id") UUID id);
    int insertMemberScope(OrganizationMemberScope scope);
    int updateMemberScope(OrganizationMemberScope scope);
    int deleteMemberScope(@Param("id") UUID id, @Param("membershipId") UUID membershipId);
}
