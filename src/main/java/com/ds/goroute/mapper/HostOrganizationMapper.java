package com.ds.goroute.mapper;

import com.ds.goroute.entity.HostOrganization;
import com.ds.goroute.entity.OrganizationMember;
import com.ds.goroute.entity.OrganizationMemberScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface HostOrganizationMapper {
    int insertOrganization(HostOrganization organization);
    int updateOrganization(HostOrganization organization);
    int updateVerificationState(HostOrganization organization);
    int updateBillingProfile(@Param("id") UUID id, @Param("dataVersion") long dataVersion,
                             @Param("commissionPercent") BigDecimal commissionPercent,
                             @Param("billingEmail") String billingEmail,
                             @Param("clearBillingEmail") boolean clearBillingEmail,
                             @Param("billingDetails") String billingDetails,
                             @Param("updatedAt") LocalDateTime updatedAt);
    List<com.ds.goroute.entity.VerificationQueueEntry> findVerificationQueue(@Param("limit") int limit, @Param("offset") int offset);
    long countVerificationQueue();
    List<UUID> findAllOrganizationIds();
    HostOrganization findOrganizationById(@Param("id") UUID id);
    List<HostOrganization> findOrganizationsForUser(@Param("userId") UUID userId);
    List<HostOrganization> findOrganizationsAdmin(@Param("query") String query,
                                                  @Param("status") List<String> status,
                                                  @Param("organizationType") List<String> organizationType,
                                                  @Param("verificationStatus") List<String> verificationStatus,
                                                  @Param("sort") String sort,
                                                  @Param("descending") boolean descending,
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
