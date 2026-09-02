package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One account's subscription row as stored, expiry included.
 *
 * <p>Read through a result map rather than as a generic map: a map hands back whatever the driver
 * produced for a TIMESTAMP, which is a {@code java.sql.Timestamp}, and the cast to
 * {@link LocalDateTime} only fails once the code is running.
 *
 * <p>{@code tier} here is the stored value, which is not necessarily the tier in force -- a period
 * that has run out still reads PRO on the row. The effective tier comes from
 * {@code AiTripMapper.getSubscriptionTier}, which is where the expiry is applied.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSubscription {
    private UUID userId;
    private String tier;
    private String planCode;
    private Integer aiTripsUsed;
    private LocalDateTime startedAt;
    private LocalDateTime expiresAt;
}
