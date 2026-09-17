package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.AiTripDraft;
import com.ds.goroute.mapper.AiTripMapper;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.service.UserSubscriptionBootstrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AiTripRepositoryImpl implements AiTripRepository {

    private final AiTripMapper aiTripMapper;
    private final UserSubscriptionBootstrapService subscriptionBootstrap;

    /**
     * Creates the default subscription only when this transaction cannot already see one.
     *
     * <p>The check is not an optimisation. {@link UserSubscriptionBootstrapService#ensureExists}
     * runs REQUIRES_NEW, so on a second pooled connection: a caller that is already inside a
     * transaction spends two connections on every call, and the ten-connection pool is shared
     * with everything else. Worse, once a caller has taken the user_subscriptions row lock, that
     * nested {@code INSERT ... ON CONFLICT DO NOTHING} waits on the tuple the suspended outer
     * transaction holds, which PostgreSQL cannot see as a cycle. Reading first means the
     * bootstrap only ever runs the first time a user is seen, when no lock can exist yet, and
     * the row is still created exactly as before.
     */
    @Override
    public void ensureSubscription(UUID userId) {
        if (aiTripMapper.getSubscriptionTier(userId) != null) {
            return;
        }
        subscriptionBootstrap.ensureExists(userId);
    }

    @Override
    public int consumeAiTripQuota(UUID userId, int limit) {
        return aiTripMapper.consumeAiTripQuota(userId, limit);
    }

    @Override
    public int releaseAiTripQuota(UUID userId) {
        return aiTripMapper.releaseAiTripQuota(userId);
    }

    /**
     * The hot read path: social submit, check-in and quota reserve all call it from inside their
     * own transaction. Reading before bootstrapping keeps that to the one connection the caller
     * already holds instead of the two the unconditional REQUIRES_NEW bootstrap needed.
     */
    @Override
    public String getSubscriptionTier(UUID userId) {
        String tier = aiTripMapper.getSubscriptionTier(userId);
        if (tier != null) {
            return tier;
        }
        subscriptionBootstrap.ensureExists(userId);
        return aiTripMapper.getSubscriptionTier(userId);
    }

    @Override
    public java.time.LocalDateTime getSubscriptionExpiresAt(UUID userId) {
        return aiTripMapper.getSubscriptionExpiresAt(userId);
    }

    @Override
    public int getAiTripsUsed(UUID userId) {
        // Deliberately NO ensureSubscription() here. ensureExists runs REQUIRES_NEW on a second
        // connection; when the caller's transaction has already locked the user's row (quota
        // consume UPDATE in AiTripQuotaService.reserve), that nested INSERT ... ON CONFLICT waits
        // on our own lock forever — a self-deadlock Postgres cannot detect. Callers that may run
        // before the row exists (getUsage/reserve) call ensureSubscription() explicitly first,
        // while the row is still unlocked.
        Integer used = aiTripMapper.getAiTripsUsed(userId);
        return used != null ? used : 0;
    }

    @Override
    public void insertDraft(AiTripDraft draft) {
        aiTripMapper.insertDraft(draft);
    }

    @Override
    public Optional<AiTripDraft> findDraftForUpdate(UUID draftId, UUID userId) {
        return Optional.ofNullable(aiTripMapper.findDraftForUpdate(draftId, userId));
    }

    @Override
    public Optional<AiTripDraft> findDraft(UUID draftId, UUID userId) {
        return Optional.ofNullable(aiTripMapper.findDraft(draftId, userId));
    }

    @Override
    public void completeDraft(UUID draftId, UUID userId, String idempotencyKey, UUID tripId) {
        aiTripMapper.completeDraft(draftId, userId, idempotencyKey, tripId);
    }
}
