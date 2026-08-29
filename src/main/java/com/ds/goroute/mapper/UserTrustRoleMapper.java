package com.ds.goroute.mapper;

import com.ds.goroute.entity.UserTrustRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface UserTrustRoleMapper {

    int insert(UserTrustRole role);

    int decide(@Param("id") UUID id,
               @Param("status") String status,
               @Param("decidedBy") UUID decidedBy,
               @Param("decisionNote") String decisionNote,
               @Param("reviewDueAt") java.time.LocalDateTime reviewDueAt);

    UserTrustRole findById(@Param("id") UUID id);

    List<UserTrustRole> findByUser(@Param("userId") UUID userId);

    /** Roles currently in force, used to decorate a public profile. */
    List<UserTrustRole> findApprovedByUser(@Param("userId") UUID userId);

    List<UserTrustRole> findQueue(@Param("status") String status,
                                  @Param("role") String role,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);

    long countQueue(@Param("status") String status, @Param("role") String role);

    /** Approved roles whose review date has passed; a permanent badge stops meaning anything. */
    List<UserTrustRole> findDueForReview(@Param("limit") int limit);
}
