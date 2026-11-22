package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.AiTripUsage;
import com.ds.goroute.dto.response.AiCreditWalletResponse;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.type.AiCreditFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * What an AI-generated trip needs before it may start: credits for the generation and for saving
 * the result as a trip (AI_CREDIT.MIN_BALANCE_AI_TRIP + AI_CREDIT.TRIP_CREATION_COST).
 *
 * <p>Both are checked up front so a run that could never be saved does not start and spend
 * credits. The trip itself is charged at commit, where it may overdraw: holding credits across a
 * generation that can take minutes would strand them whenever the run fails.
 */
@Service
@RequiredArgsConstructor
public class AiTripQuotaService {
    private final AiTripRepository repository;
    private final AiCreditService credits;

    @Transactional
    public AiTripUsage getUsage(UUID userId) {
        repository.ensureSubscription(userId);
        boolean creditAvailable = credits.canStartTrip(userId, AiCreditFeature.AI_TRIP);
        AiCreditWalletResponse wallet = credits.wallet(userId);
        int cost = wallet.getTripCreationCost();
        boolean tripAffordable = wallet.isUnlimited()
                || wallet.getBalance().compareTo(BigDecimal.valueOf(cost)) >= 0;
        return AiTripUsage.builder()
                .tier(repository.getSubscriptionTier(userId))
                .eligible(creditAvailable)
                .creditAvailable(creditAvailable)
                .tripCreationCost(cost)
                .tripQuotaAvailable(tripAffordable)
                .freeTripQuotaUsed(0)
                .freeTripQuota(0)
                .unlockedTripSlots(cost <= 0 ? 0 : wallet.getBalance().max(BigDecimal.ZERO)
                        .divide(BigDecimal.valueOf(cost), 0, RoundingMode.FLOOR).intValue())
                .tierExpiresAt(repository.getSubscriptionExpiresAt(userId))
                .build();
    }

    /**
     * Refuses a generation that cannot finish ({@link ErrorConstant#AI_CREDIT_INSUFFICIENT}):
     * fewer credits than the AI minimum plus a trip creation. {@code extraFeatures} add their own
     * minimums, for a social video that is extracted and then planned.
     */
    @Transactional
    public void ensureCanGenerate(UUID userId, AiCreditFeature... extraFeatures) {
        repository.ensureSubscription(userId);
        AiCreditFeature[] features = new AiCreditFeature[extraFeatures.length + 1];
        features[0] = AiCreditFeature.AI_TRIP;
        System.arraycopy(extraFeatures, 0, features, 1, extraFeatures.length);
        credits.ensureCanStartTrip(userId, features);
    }
}
