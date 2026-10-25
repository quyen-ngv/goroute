package com.ds.goroute.repository.impl;

import com.ds.goroute.mapper.AiTripMapper;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.service.UserSubscriptionBootstrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
}
