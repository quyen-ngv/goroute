package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One thing a user can buy: a tier, for a length of time, at a price. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan {
    private String code;
    private String tier;
    private String displayName;
    private Integer durationDays;
    private BigDecimal priceAmount;
    private String priceCurrency;
    private Boolean isActive;
    private Integer sortOrder;
    /** AI credits the plan bucket is reset to every refill period (V254). */
    private BigDecimal aiCreditsPerRefill;
    /** Refill period in months: 1 monthly, 3 quarterly, 12 yearly. */
    private Integer aiCreditRefillMonths;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
