package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.UpdateUserQuotaOverridesRequest;
import com.ds.goroute.dto.response.AdminUserQuotaResponse;
import com.ds.goroute.dto.response.UserQuotaLimitResponse;
import com.ds.goroute.entity.User;
import com.ds.goroute.entity.UserQuotaOverride;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.UserQuotaOverrideRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.type.BusinessConfigKey;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/** Admin read/update use case for per-user quota overrides. */
@Service
@RequiredArgsConstructor
public class AdminUserQuotaService {
    private final UserRepository userRepository;
    private final UserQuotaOverrideRepository overrideRepository;
    private final UserQuotaPolicyService policy;
    private final BusinessConfigService businessConfig;

    @Transactional(readOnly = true)
    public AdminUserQuotaResponse get(UUID userId) {
        ensureUser(userId);
        UserQuotaOverride override = policy.findOverride(userId).orElse(null);
        return response(userId, override);
    }

    @Transactional
    public AdminUserQuotaResponse update(UUID userId, UpdateUserQuotaOverridesRequest request) {
        ensureUser(userId);
        UserQuotaOverride current = overrideRepository.findByUserId(userId).orElse(null);
        long expectedVersion = request.getExpectedVersion() == null
                ? current == null ? 0L : current.getDataVersion()
                : request.getExpectedVersion();
        LocalDateTime now = LocalDateTime.now();

        if (current == null) {
            if (expectedVersion != 0L) {
                throw staleVersion();
            }
            UserQuotaOverride created = UserQuotaOverride.builder()
                    .userId(userId)
                    .freeTripQuota(request.getFreeTripQuota())
                    .aiTripFreeQuota(request.getAiTripFreeQuota())
                    .aiTripProQuota(request.getAiTripProQuota())
                    .socialLocationDailyLimit(request.getSocialLocationDailyLimit())
                    .dataVersion(1L)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            try {
                overrideRepository.insert(created);
            } catch (DataIntegrityViolationException exception) {
                throw staleVersion();
            }
        } else {
            current.setFreeTripQuota(request.getFreeTripQuota());
            current.setAiTripFreeQuota(request.getAiTripFreeQuota());
            current.setAiTripProQuota(request.getAiTripProQuota());
            current.setSocialLocationDailyLimit(request.getSocialLocationDailyLimit());
            current.setDataVersion(expectedVersion);
            current.setUpdatedAt(now);
            if (overrideRepository.update(current) != 1) {
                throw staleVersion();
            }
        }
        policy.evict(userId);
        return get(userId);
    }

    private AdminUserQuotaResponse response(UUID userId, UserQuotaOverride override) {
        int globalFreeTrip = businessConfig.getInt(BusinessConfigKey.TRIP_FREE_CREATION_QUOTA);
        int globalAiFree = businessConfig.getInt(BusinessConfigKey.AI_TRIP_FREE_QUOTA);
        int globalAiPro = businessConfig.getInt(BusinessConfigKey.AI_TRIP_PRO_QUOTA);
        int globalSocial = businessConfig.getInt(BusinessConfigKey.SOCIAL_LOCATION_DAILY_JOB_LIMIT);
        return AdminUserQuotaResponse.builder()
                .userId(userId)
                .dataVersion(override == null ? 0L : override.getDataVersion())
                .freeTrip(limit(globalFreeTrip, override == null ? null : override.getFreeTripQuota()))
                .aiTripFree(limit(globalAiFree, override == null ? null : override.getAiTripFreeQuota()))
                .aiTripPro(limit(globalAiPro, override == null ? null : override.getAiTripProQuota()))
                .socialLocationDaily(limit(globalSocial,
                        override == null ? null : override.getSocialLocationDailyLimit()))
                .build();
    }

    private UserQuotaLimitResponse limit(int globalLimit, Integer overrideLimit) {
        return UserQuotaLimitResponse.builder()
                .globalLimit(globalLimit)
                .overrideLimit(overrideLimit)
                .effectiveLimit(overrideLimit == null ? globalLimit : overrideLimit)
                .build();
    }

    private User ensureUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));
    }

    private BusinessException staleVersion() {
        return new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                "User quota settings were changed by another administrator");
    }
}
