package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.UserTrustRole;
import com.ds.goroute.mapper.UserTrustRoleMapper;
import com.ds.goroute.repository.UserTrustRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserTrustRoleRepositoryImpl implements UserTrustRoleRepository {

    private final UserTrustRoleMapper mapper;

    @Override
    public int insert(UserTrustRole role) {
        return mapper.insert(role);
    }

    @Override
    public int decide(UUID id, String status, UUID decidedBy, String decisionNote, LocalDateTime reviewDueAt) {
        return mapper.decide(id, status, decidedBy, decisionNote, reviewDueAt);
    }

    @Override
    public Optional<UserTrustRole> findById(UUID id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public List<UserTrustRole> findByUser(UUID userId) {
        return mapper.findByUser(userId);
    }

    @Override
    public List<UserTrustRole> findApprovedByUser(UUID userId) {
        return mapper.findApprovedByUser(userId);
    }

    @Override
    public List<UserTrustRole> findQueue(String status, String role, int limit, int offset) {
        return mapper.findQueue(status, role, limit, offset);
    }

    @Override
    public long countQueue(String status, String role) {
        return mapper.countQueue(status, role);
    }

    @Override
    public List<UserTrustRole> findDueForReview(int limit) {
        return mapper.findDueForReview(limit);
    }
}
