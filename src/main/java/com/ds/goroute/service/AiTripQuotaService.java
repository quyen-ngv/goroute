package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.AiTripUsage;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.AiTripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The two allowances an AI-generated trip spends, priced together.
 *
 * <p>A generation costs a generation slot when it starts and a trip-creation slot when the result
 * is saved. Charging the first without looking at the second is what produced the dead end this
 * class now closes: the generation slot gone, the model bill paid, and a refusal at the last step
 * with nothing to show for it. Both are quoted and both are checked before anything is spent.
 */
@Service
@RequiredArgsConstructor
public class AiTripQuotaService {
    public static final int FREE_LIMIT = 3;
    public static final int PRO_LIMIT = 10;

    private final AiTripRepository repository;
    private final StarService starService;

    @Transactional
    public AiTripUsage getUsage(UUID userId) {
        repository.ensureSubscription(userId);
        String tier = repository.getSubscriptionTier(userId);
        return usage(userId, tier, repository.getAiTripsUsed(userId));
    }

    /**
     * Takes a generation slot, refusing when either allowance is empty.
     *
     * <p>The trip-creation side is checked first and deliberately not consumed here: the trip does
     * not exist yet, and holding a slot across a generation that can take minutes would strand it
     * whenever the run fails. It is consumed by trip creation at commit time, as it always was --
     * this check only stops a run that could not possibly finish.
     */
    @Transactional
    public AiTripUsage reserve(UUID userId) {
        repository.ensureSubscription(userId);
        String tier = repository.getSubscriptionTier(userId);
        int limit = limitForTier(tier);

        if (!starService.tripCreationStatus(userId).available()) {
            throw new BusinessException(ErrorConstant.TRIP_CREATION_QUOTA_EXHAUSTED,
                    "You are out of trip creation slots, so an AI itinerary could not be saved. "
                            + "Unlock a slot with stars before generating one.");
        }
        if (repository.consumeAiTripQuota(userId, limit) == 0) {
            throw new BusinessException(ErrorConstant.AI_TRIP_QUOTA_EXHAUSTED);
        }
        return usage(userId, tier, repository.getAiTripsUsed(userId));
    }

    @Transactional
    public void release(UUID userId) {
        repository.releaseAiTripQuota(userId);
    }

    public int limitForTier(String tier) {
        return "PRO".equalsIgnoreCase(tier) ? PRO_LIMIT : FREE_LIMIT;
    }

    private AiTripUsage usage(UUID userId, String tier, int used) {
        int limit = limitForTier(tier);
        StarService.TripCreationStatus tripQuota = starService.tripCreationStatus(userId);
        boolean aiAvailable = used < limit;
        return AiTripUsage.builder()
                .tier(tier)
                .used(used)
                .limit(limit)
                // Both, because the client asks this one question to decide whether to offer the
                // button at all, and either empty allowance is a reason not to.
                .eligible(aiAvailable && tripQuota.available())
                .aiQuotaAvailable(aiAvailable)
                .tripQuotaAvailable(tripQuota.available())
                .freeTripQuotaUsed(tripQuota.freeQuotaUsed())
                .freeTripQuota(tripQuota.freeQuota())
                .unlockedTripSlots(tripQuota.unlockedSlots())
                .tierExpiresAt(repository.getSubscriptionExpiresAt(userId))
                .build();
    }
}
