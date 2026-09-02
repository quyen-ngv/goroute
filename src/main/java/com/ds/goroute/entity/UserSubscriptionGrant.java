package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One period of paid access, as it was granted.
 *
 * <p>Append-only, like the point ledger and for the same reason: "how long has this account been
 * Pro, and who gave it to them" has to be answerable after the fact, and a row that can be edited
 * cannot answer it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSubscriptionGrant {
    private UUID id;
    private UUID userId;
    private String planCode;
    private String tier;
    private Integer durationDays;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;

    /** Idempotency key. The same key never grants a second period. */
    private String referenceKey;

    /** The operator, for an admin grant; null when a payment produced it. */
    private UUID grantedBy;
    private String note;
    private LocalDateTime createdAt;
}
