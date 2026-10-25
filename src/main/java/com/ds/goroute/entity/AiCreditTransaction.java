package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One append-only entry of {@code ai_credit_transactions}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCreditTransaction {
    private UUID id;
    private UUID userId;
    private String transactionType;
    /** planAmount + purchasedAmount; negative for a charge. */
    private BigDecimal amount;
    private BigDecimal planAmount;
    private BigDecimal purchasedAmount;
    private BigDecimal planBalanceAfter;
    private BigDecimal purchasedBalanceAfter;
    private String referenceKey;
    private String feature;
    private String traceId;
    private UUID aiApiCallId;
    private BigDecimal costUsd;
    private UUID reversesTransactionId;
    private String note;
    private UUID createdBy;
    private LocalDateTime createdAt;
}
