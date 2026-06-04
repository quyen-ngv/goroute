package com.ds.goroute.mapper;

import com.ds.goroute.entity.AiTripDraft;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface AiTripMapper {
    int ensureSubscription(@Param("userId") UUID userId);

    int consumeAiTripQuota(@Param("userId") UUID userId);

    String getSubscriptionTier(@Param("userId") UUID userId);

    Integer getAiTripsUsed(@Param("userId") UUID userId);

    int insertDraft(AiTripDraft draft);

    AiTripDraft findDraftForUpdate(@Param("draftId") UUID draftId, @Param("userId") UUID userId);

    AiTripDraft findDraft(@Param("draftId") UUID draftId, @Param("userId") UUID userId);

    int completeDraft(@Param("draftId") UUID draftId,
                      @Param("userId") UUID userId,
                      @Param("idempotencyKey") String idempotencyKey,
                      @Param("tripId") UUID tripId);
}
