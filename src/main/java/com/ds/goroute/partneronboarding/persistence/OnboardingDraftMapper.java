package com.ds.goroute.partneronboarding.persistence;

import com.ds.goroute.partneronboarding.domain.OnboardingDraft;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface OnboardingDraftMapper {

    int insert(OnboardingDraft draft);

    OnboardingDraft findById(@Param("id") UUID id);

    List<OnboardingDraft> findByUser(@Param("userId") UUID userId,
                                     @Param("status") String status,
                                     @Param("limit") int limit,
                                     @Param("offset") int offset);

    long countByUser(@Param("userId") UUID userId, @Param("status") String status);

    /**
     * Writes one step's answers. Guarded by {@code data_version} so two devices editing the
     * same draft cannot silently overwrite each other; zero rows updated means a conflict.
     */
    int updateStep(@Param("id") UUID id,
                   @Param("expectedVersion") long expectedVersion,
                   @Param("currentStep") String currentStep,
                   @Param("completedSteps") String completedSteps,
                   @Param("data") String data,
                   @Param("updatedAt") LocalDateTime updatedAt);

    /** Attaches the organization the wizard created or picked; only ever set once. */
    int updateOrganization(@Param("id") UUID id,
                           @Param("expectedVersion") long expectedVersion,
                           @Param("organizationId") UUID organizationId,
                           @Param("updatedAt") LocalDateTime updatedAt);

    int updateStatus(@Param("id") UUID id,
                     @Param("expectedVersion") long expectedVersion,
                     @Param("status") String status,
                     @Param("resultHotelId") UUID resultHotelId,
                     @Param("resultActivityId") UUID resultActivityId,
                     @Param("submittedAt") LocalDateTime submittedAt,
                     @Param("updatedAt") LocalDateTime updatedAt);
}
