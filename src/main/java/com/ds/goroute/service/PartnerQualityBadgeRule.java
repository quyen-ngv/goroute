package com.ds.goroute.service;

import com.ds.goroute.type.PartnerQualityBadge;

import java.math.BigDecimal;

/**
 * Badge thresholds for a partner quality window. Rates are percentages (0-100), the review
 * average is on the 1-5 scale. A missing figure never qualifies for PREFERRED and counts as
 * "no evidence" (not as a problem) for AT_RISK.
 */
public final class PartnerQualityBadgeRule {
    static final int PREFERRED_MIN_BOOKINGS = 10;
    static final BigDecimal PREFERRED_MAX_HOST_CANCELLATION = BigDecimal.ONE;
    static final BigDecimal PREFERRED_MAX_NO_SHOW = BigDecimal.valueOf(2);
    static final BigDecimal PREFERRED_MIN_SLA_RATE = BigDecimal.valueOf(90);
    static final BigDecimal PREFERRED_MIN_REVIEW_AVERAGE = new BigDecimal("4.5");
    static final BigDecimal AT_RISK_HOST_CANCELLATION = BigDecimal.valueOf(5);
    static final BigDecimal AT_RISK_EXPIRY = BigDecimal.valueOf(20);
    static final int RISING_MIN_BOOKINGS = 3;

    private PartnerQualityBadgeRule() {
    }

    public static PartnerQualityBadge evaluate(int bookingsTotal, BigDecimal hostCancellationRate, BigDecimal noShowRate,
                                               BigDecimal expiryRate, BigDecimal responseWithinSlaRate, BigDecimal reviewAverage) {
        if (atLeast(hostCancellationRate, AT_RISK_HOST_CANCELLATION) || atLeast(expiryRate, AT_RISK_EXPIRY)) {
            return PartnerQualityBadge.AT_RISK;
        }
        boolean preferred = bookingsTotal >= PREFERRED_MIN_BOOKINGS
                && below(hostCancellationRate, PREFERRED_MAX_HOST_CANCELLATION)
                && below(noShowRate, PREFERRED_MAX_NO_SHOW)
                && atLeast(responseWithinSlaRate, PREFERRED_MIN_SLA_RATE)
                && atLeast(reviewAverage, PREFERRED_MIN_REVIEW_AVERAGE);
        if (preferred) return PartnerQualityBadge.PREFERRED;
        if (bookingsTotal >= RISING_MIN_BOOKINGS) return PartnerQualityBadge.RISING;
        return PartnerQualityBadge.NONE;
    }

    private static boolean atLeast(BigDecimal value, BigDecimal threshold) {
        return value != null && value.compareTo(threshold) >= 0;
    }

    private static boolean below(BigDecimal value, BigDecimal threshold) {
        return value != null && value.compareTo(threshold) < 0;
    }
}
