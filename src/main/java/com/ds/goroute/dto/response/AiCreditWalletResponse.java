package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** A user's AI credits as the app shows them. Amounts are credits, 4 decimals. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCreditWalletResponse {
    /** planBalance + purchasedBalance; negative after a task finished in debt. */
    private BigDecimal balance;
    private BigDecimal planBalance;
    private BigDecimal purchasedBalance;
    /** FREE or the subscription plan code the current period came from. */
    private String planCode;
    /** What the plan refills to each period. */
    private BigDecimal planCredits;
    private int refillMonths;
    private LocalDateTime nextRefillAt;
    /** A running task stops once the balance is below minus this (not for beta users). */
    private int negativeFloor;
    /** Balance each feature needs to start, keyed by AiCreditFeature name. */
    private Map<String, Integer> minBalance;
    /** Beta users: still charged, never refused or stopped. */
    private boolean unlimited;
}
