package com.ds.goroute.repository;

import com.ds.goroute.entity.UserQuotaOverride;

import java.util.Optional;
import java.util.UUID;

public interface UserQuotaOverrideRepository {
    Optional<UserQuotaOverride> findByUserId(UUID userId);

    int insert(UserQuotaOverride override);

    int update(UserQuotaOverride override);
}
