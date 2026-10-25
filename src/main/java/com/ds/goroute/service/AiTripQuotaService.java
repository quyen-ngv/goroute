package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.AiTripUsage;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.type.AiCreditFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * What an AI-generated trip needs before it may start: AI credits for the generation, and a
 * trip-creation slot for the result.
 *
 * <p>The trip-creation side is checked so a run that could never be saved does not start and
 * spend credits: the model bill paid, and a refusal at the last step with nothing to show for it.
 * The slot itself is consumed by trip creation at commit time, not here: holding it across a
 * generation that can take minutes would strand it whenever the run fails.
 */
@Service
@RequiredArgsConstructor
public class AiTripQuotaService {
    private final AiTripRepository repository;
    private final StarService starService;
    private final AiCreditService credits;

    @Transactional
    public AiTripUsage getUsage(UUID userId) {
        repository.ensureSubscription(userId);
        StarService.TripCreationStatus tripQuota = starService.tripCreationStatus(userId);
        boolean creditAvailable = credits.canStart(userId, AiCreditFeature.AI_TRIP);
        return AiTripUsage.builder()
                .tier(repository.getSubscriptionTier(userId))
                // Both, because the client asks this one question to decide whether to offer the
                // button at all, and either empty allowance is a reason not to.
                .eligible(creditAvailable && tripQuota.available())
                .creditAvailable(creditAvailable)
                .tripQuotaAvailable(tripQuota.available())
                .freeTripQuotaUsed(tripQuota.freeQuotaUsed())
                .freeTripQuota(tripQuota.freeQuota())
                .unlockedTripSlots(tripQuota.unlockedSlots())
                .tierExpiresAt(repository.getSubscriptionExpiresAt(userId))
                .build();
    }

    /**
     * Refuses a generation that cannot finish: no trip-creation slot, or too few AI credits
     * ({@link ErrorConstant#AI_CREDIT_INSUFFICIENT}). {@code extraFeatures} add their own minimums,
     * for a social video that is extracted and then planned.
     */
    @Transactional
    public void ensureCanGenerate(UUID userId, AiCreditFeature... extraFeatures) {
        repository.ensureSubscription(userId);
        if (!starService.tripCreationStatus(userId).available()) {
            throw new BusinessException(ErrorConstant.TRIP_CREATION_QUOTA_EXHAUSTED,
                    "You are out of trip creation slots, so an AI itinerary could not be saved. "
                            + "Unlock a slot with stars before generating one.");
        }
        AiCreditFeature[] features = new AiCreditFeature[extraFeatures.length + 1];
        features[0] = AiCreditFeature.AI_TRIP;
        System.arraycopy(extraFeatures, 0, features, 1, extraFeatures.length);
        credits.ensureCanStart(userId, features);
    }
}
