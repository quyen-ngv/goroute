package com.ds.goroute.mapper;

import com.ds.goroute.entity.AiTripDraft;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface AiTripMapper {
    int ensureSubscription(@Param("userId") UUID userId);

    int consumeAiTripQuota(@Param("userId") UUID userId, @Param("limit") int limit);

    int releaseAiTripQuota(@Param("userId") UUID userId);

    /** The tier in force right now: a lapsed paid plan reads as FREE. */
    String getSubscriptionTier(@Param("userId") UUID userId);

    /** When the current paid period ends, or null for free and open-ended accounts. */
    java.time.LocalDateTime getSubscriptionExpiresAt(@Param("userId") UUID userId);

    Integer getAiTripsUsed(@Param("userId") UUID userId);

    int insertDraft(AiTripDraft draft);

    AiTripDraft findDraftForUpdate(@Param("draftId") UUID draftId, @Param("userId") UUID userId);

    AiTripDraft findDraft(@Param("draftId") UUID draftId, @Param("userId") UUID userId);

    int completeDraft(@Param("draftId") UUID draftId,
                      @Param("userId") UUID userId,
                      @Param("idempotencyKey") String idempotencyKey,
                      @Param("tripId") UUID tripId);
}
