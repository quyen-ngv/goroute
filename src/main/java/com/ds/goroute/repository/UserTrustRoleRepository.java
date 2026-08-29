package com.ds.goroute.repository;

import com.ds.goroute.entity.UserTrustRole;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence for granted community roles (TRUST-02). */
public interface UserTrustRoleRepository {

    int insert(UserTrustRole role);

    int decide(UUID id, String status, UUID decidedBy, String decisionNote, LocalDateTime reviewDueAt);

    Optional<UserTrustRole> findById(UUID id);

    List<UserTrustRole> findByUser(UUID userId);

    List<UserTrustRole> findApprovedByUser(UUID userId);

    List<UserTrustRole> findQueue(String status, String role, int limit, int offset);

    long countQueue(String status, String role);

    List<UserTrustRole> findDueForReview(int limit);
}
