package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StarTransaction {
    private UUID id;
    private UUID userId;
    /** Positive when earned, negative when spent. */
    private Integer amount;
    private String transactionType;
    /** Idempotency key, unique across the ledger. */
    private String referenceKey;
    private String description;
    /** Balance immediately after this entry, so the ledger can be read without replaying it. */
    private Integer balanceAfter;
    /**
     * Set on a reversal. A refund is a new opposite entry pointing at the original, never
     * an edit of it: history that can be rewritten cannot be reconciled.
     */
    private UUID reversesTransactionId;
    /** The operator, on a manual adjustment. */
    private UUID createdBy;
    private String reason;
    private LocalDateTime createdAt;
}
