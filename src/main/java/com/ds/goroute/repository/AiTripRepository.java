package com.ds.goroute.repository;

import com.ds.goroute.entity.AiTripDraft;

import java.util.Optional;
import java.util.UUID;

public interface AiTripRepository {
    void ensureSubscription(UUID userId);

    int consumeAiTripQuota(UUID userId);

    String getSubscriptionTier(UUID userId);

    int getAiTripsUsed(UUID userId);

    void insertDraft(AiTripDraft draft);

    Optional<AiTripDraft> findDraftForUpdate(UUID draftId, UUID userId);

    Optional<AiTripDraft> findDraft(UUID draftId, UUID userId);

    void completeDraft(UUID draftId, UUID userId, String idempotencyKey, UUID tripId);
}
