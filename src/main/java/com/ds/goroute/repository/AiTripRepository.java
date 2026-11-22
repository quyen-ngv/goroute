package com.ds.goroute.repository;

import java.util.UUID;

public interface AiTripRepository {
    void ensureSubscription(UUID userId);

    String getSubscriptionTier(UUID userId);

    /** When the current paid period ends, or null for free and open-ended accounts. */
    java.time.LocalDateTime getSubscriptionExpiresAt(UUID userId);
}
