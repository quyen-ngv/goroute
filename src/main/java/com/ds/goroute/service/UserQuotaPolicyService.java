package com.ds.goroute.service;

import com.ds.goroute.entity.UserQuotaOverride;
import com.ds.goroute.repository.UserQuotaOverrideRepository;
import com.ds.goroute.type.BusinessConfigKey;
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
    private final BusinessConfigService businessConfig;

    @Cacheable(cacheNames = "userQuotaOverrides", key = "#userId", sync = true)
    public Optional<UserQuotaOverride> findOverride(UUID userId) {
        return repository.findByUserId(userId);
    }

    @CacheEvict(cacheNames = "userQuotaOverrides", key = "#userId")
    public void evict(UUID userId) {
        // The annotation is the operation; keeping this method explicit makes the cache boundary
        // visible to the admin update use case.
    }

    public int freeTripQuota(UUID userId) {
        return findOverride(userId).map(UserQuotaOverride::getFreeTripQuota)
                .orElseGet(() -> businessConfig.getInt(BusinessConfigKey.TRIP_FREE_CREATION_QUOTA));
    }

    public int aiTripQuota(UUID userId, String tier) {
        boolean pro = "PRO".equalsIgnoreCase(tier);
        Optional<UserQuotaOverride> override = findOverride(userId);
        if (pro && override.isPresent() && override.get().getAiTripProQuota() != null) {
            return override.get().getAiTripProQuota();
        }
        if (!pro && override.isPresent() && override.get().getAiTripFreeQuota() != null) {
            return override.get().getAiTripFreeQuota();
        }
        return businessConfig.getInt(pro
                ? BusinessConfigKey.AI_TRIP_PRO_QUOTA
                : BusinessConfigKey.AI_TRIP_FREE_QUOTA);
    }

    public int socialLocationDailyLimit(UUID userId) {
        return findOverride(userId).map(UserQuotaOverride::getSocialLocationDailyLimit)
                .orElseGet(() -> businessConfig.getInt(BusinessConfigKey.SOCIAL_LOCATION_DAILY_JOB_LIMIT));
    }
}
