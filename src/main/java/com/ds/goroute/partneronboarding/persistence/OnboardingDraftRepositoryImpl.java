package com.ds.goroute.partneronboarding.persistence;

import com.ds.goroute.partneronboarding.domain.DraftStatus;
import com.ds.goroute.partneronboarding.domain.OnboardingDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OnboardingDraftRepositoryImpl implements OnboardingDraftRepository {

    private final OnboardingDraftMapper mapper;

    @Override
    public void insert(OnboardingDraft draft) {
        mapper.insert(draft);
    }

    @Override
    public Optional<OnboardingDraft> findById(UUID id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public List<OnboardingDraft> findByUser(UUID userId, DraftStatus status, int limit, int offset) {
        return mapper.findByUser(userId, name(status), limit, offset);
    }

    @Override
    public long countByUser(UUID userId, DraftStatus status) {
        return mapper.countByUser(userId, name(status));
    }

    @Override
    public boolean updateStep(UUID id, long expectedVersion, String currentStep,
                              String completedSteps, String data, LocalDateTime updatedAt) {
        return mapper.updateStep(id, expectedVersion, currentStep, completedSteps, data, updatedAt) == 1;
    }

    @Override
    public boolean updateOrganization(UUID id, long expectedVersion, UUID organizationId, LocalDateTime updatedAt) {
        return mapper.updateOrganization(id, expectedVersion, organizationId, updatedAt) == 1;
    }

    @Override
    public boolean updateStatus(UUID id, long expectedVersion, DraftStatus status, UUID resultHotelId,
                                UUID resultActivityId, LocalDateTime submittedAt, LocalDateTime updatedAt) {
        return mapper.updateStatus(id, expectedVersion, status.name(), resultHotelId,
                resultActivityId, submittedAt, updatedAt) == 1;
    }

    private static String name(DraftStatus status) {
        return status == null ? null : status.name();
    }
}
