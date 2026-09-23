package com.ds.goroute.partneronboarding.persistence;

import com.ds.goroute.partneronboarding.domain.DraftStatus;
import com.ds.goroute.partneronboarding.domain.OnboardingDraft;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for onboarding drafts. Keeps MyBatis out of the service and turns the
 * optimistic-lock "rows updated" contract into a boolean the service can act on.
 */
public interface OnboardingDraftRepository {

    void insert(OnboardingDraft draft);

    Optional<OnboardingDraft> findById(UUID id);

    List<OnboardingDraft> findByUser(UUID userId, DraftStatus status, int limit, int offset);

    long countByUser(UUID userId, DraftStatus status);

    /** @return false when another writer moved the draft on first */
    boolean updateStep(UUID id, long expectedVersion, String currentStep,
                       String completedSteps, String data, LocalDateTime updatedAt);

    boolean updateOrganization(UUID id, long expectedVersion, UUID organizationId, LocalDateTime updatedAt);

    boolean updateStatus(UUID id, long expectedVersion, DraftStatus status, UUID resultHotelId,
                         UUID resultActivityId, LocalDateTime submittedAt, LocalDateTime updatedAt);
}
