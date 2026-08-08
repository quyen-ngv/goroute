package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.AiTripUsage;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.AiTripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiTripQuotaService {
    public static final int FREE_LIMIT = 3;
    public static final int PRO_LIMIT = 10;

    private final AiTripRepository repository;

    public AiTripUsage getUsage(UUID userId) {
        repository.ensureSubscription(userId);
        String tier = repository.getSubscriptionTier(userId);
        int used = repository.getAiTripsUsed(userId);
        int limit = limitForTier(tier);
        return AiTripUsage.builder()
                .tier(tier)
                .used(used)
                .limit(limit)
                .eligible(used < limit)
                .build();
    }

    public AiTripUsage reserve(UUID userId) {
        repository.ensureSubscription(userId);
        String tier = repository.getSubscriptionTier(userId);
        int limit = limitForTier(tier);
        if (repository.consumeAiTripQuota(userId, limit) == 0) {
            throw new BusinessException(ErrorConstant.AI_TRIP_QUOTA_EXHAUSTED);
        }
        int used = repository.getAiTripsUsed(userId);
        return AiTripUsage.builder()
                .tier(tier)
                .used(used)
                .limit(limit)
                .eligible(used < limit)
                .build();
    }

    public void release(UUID userId) {
        repository.releaseAiTripQuota(userId);
    }

    public int limitForTier(String tier) {
        return "PRO".equalsIgnoreCase(tier) ? PRO_LIMIT : FREE_LIMIT;
    }
}
