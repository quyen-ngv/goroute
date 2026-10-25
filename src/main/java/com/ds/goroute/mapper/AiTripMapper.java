package com.ds.goroute.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface AiTripMapper {
    int ensureSubscription(@Param("userId") UUID userId);

    /** The tier in force right now: a lapsed paid plan reads as FREE. */
    String getSubscriptionTier(@Param("userId") UUID userId);

    /** When the current paid period ends, or null for free and open-ended accounts. */
    java.time.LocalDateTime getSubscriptionExpiresAt(@Param("userId") UUID userId);

}
