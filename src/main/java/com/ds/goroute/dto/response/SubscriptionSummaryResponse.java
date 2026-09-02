package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * What plan an account is on, and until when.
 *
 * <p>{@code tier} is the tier in force, so an account whose paid period has ended reads as FREE
 * here exactly as it does everywhere else. {@code expiresAt} null on a paid tier means open-ended
 * access -- comped, or granted before plans had a duration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionSummaryResponse {
    private String tier;
    private String planCode;
    private String planName;
    private LocalDateTime startedAt;
    private LocalDateTime expiresAt;

    /** True while a paid tier is in force, so the client does not have to compare strings. */
    private boolean pro;

    /** Whole days left, null when the plan is free or open-ended. Negative is never returned. */
    private Integer daysRemaining;
}
