package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.UserQuotaOverride;
import com.ds.goroute.mapper.UserQuotaOverrideMapper;
import com.ds.goroute.repository.UserQuotaOverrideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserQuotaOverrideRepositoryImpl implements UserQuotaOverrideRepository {
    private final UserQuotaOverrideMapper mapper;

    @Override
    public Optional<UserQuotaOverride> findByUserId(UUID userId) {
        return Optional.ofNullable(mapper.findByUserId(userId));
    }

    @Override
    public int insert(UserQuotaOverride override) {
        return mapper.insert(override);
    }

    @Override
    public int update(UserQuotaOverride override) {
        return mapper.update(override);
    }
}
