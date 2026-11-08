package com.ds.goroute.service;

import com.ds.goroute.entity.UserQuotaOverride;
import com.ds.goroute.repository.UserQuotaOverrideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/** Resolves a user's effective limits from global config plus optional user overrides. */
@Service
@RequiredArgsConstructor
public class UserQuotaPolicyService {
    private final UserQuotaOverrideRepository repository;

    @Cacheable(cacheNames = "userQuotaOverrides", key = "#userId", sync = true)
    public Optional<UserQuotaOverride> findOverride(UUID userId) {
        return repository.findByUserId(userId);
    }

    @CacheEvict(cacheNames = "userQuotaOverrides", key = "#userId")
    public void evict(UUID userId) {
        // The annotation is the operation; keeping this method explicit makes the cache boundary
        // visible to the admin update use case.
    }
}
