package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A user's subscription row joined with its plan's credit allowance (read-only projection). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCreditPlan {
    private String tier;
    private String planCode;
    private LocalDateTime startedAt;
    private LocalDateTime expiresAt;
    /** Null when the row names no plan, or a plan that no longer exists. */
    private BigDecimal creditsPerRefill;
    private Integer refillMonths;
}
