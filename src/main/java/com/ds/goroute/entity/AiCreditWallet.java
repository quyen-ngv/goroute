package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One user's AI credits: the plan bucket (refilled each period) and the bought bucket. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCreditWallet {
    private UUID userId;
    /** Negative when a task finished in debt; the next refill or purchase pays it back. */
    private BigDecimal planBalance;
    private BigDecimal purchasedBalance;
    /** The plan the current period was refilled from: FREE or a subscription_plans code. */
    private String planCode;
    private LocalDateTime planPeriodStart;
    private LocalDateTime planPeriodEnd;
    private BigDecimal lifetimeSpent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BigDecimal balance() {
        return planBalance.add(purchasedBalance);
    }
}
