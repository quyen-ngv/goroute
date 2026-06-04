package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.AiTripDraft;
import com.ds.goroute.mapper.AiTripMapper;
import com.ds.goroute.repository.AiTripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AiTripRepositoryImpl implements AiTripRepository {

    private final AiTripMapper aiTripMapper;

    @Override
    public void ensureSubscription(UUID userId) {
        aiTripMapper.ensureSubscription(userId);
    }

    @Override
    public int consumeAiTripQuota(UUID userId) {
        return aiTripMapper.consumeAiTripQuota(userId);
    }

    @Override
    public String getSubscriptionTier(UUID userId) {
        ensureSubscription(userId);
        return aiTripMapper.getSubscriptionTier(userId);
    }

    @Override
    public int getAiTripsUsed(UUID userId) {
        ensureSubscription(userId);
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
