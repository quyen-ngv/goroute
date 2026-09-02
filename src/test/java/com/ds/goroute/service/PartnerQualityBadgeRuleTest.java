package com.ds.goroute.service;

import com.ds.goroute.type.PartnerQualityBadge;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartnerQualityBadgeRuleTest {
    private static BigDecimal d(String value) { return new BigDecimal(value); }

    @Test
    void preferredNeedsVolumeAndEveryThresholdMet() {
        assertEquals(PartnerQualityBadge.PREFERRED,
                PartnerQualityBadgeRule.evaluate(10, d("0.99"), d("1.99"), d("0"), d("90"), d("4.5")));
    }

    @Test
    void preferredFallsBackToRisingWhenOneThresholdMisses() {
        // Same figures as the PREFERRED case, each with one criterion just off.
        assertEquals(PartnerQualityBadge.RISING, PartnerQualityBadgeRule.evaluate(9, d("0"), d("0"), d("0"), d("95"), d("4.8")));
        assertEquals(PartnerQualityBadge.RISING, PartnerQualityBadgeRule.evaluate(10, d("1"), d("0"), d("0"), d("95"), d("4.8")));
        assertEquals(PartnerQualityBadge.RISING, PartnerQualityBadgeRule.evaluate(10, d("0"), d("2"), d("0"), d("95"), d("4.8")));
        assertEquals(PartnerQualityBadge.RISING, PartnerQualityBadgeRule.evaluate(10, d("0"), d("0"), d("0"), d("89.99"), d("4.8")));
        assertEquals(PartnerQualityBadge.RISING, PartnerQualityBadgeRule.evaluate(10, d("0"), d("0"), d("0"), d("95"), d("4.49")));
    }

    @Test
    void missingFiguresNeverQualifyForPreferred() {
        assertEquals(PartnerQualityBadge.RISING, PartnerQualityBadgeRule.evaluate(20, d("0"), d("0"), d("0"), null, d("5")));
        assertEquals(PartnerQualityBadge.RISING, PartnerQualityBadgeRule.evaluate(20, d("0"), d("0"), d("0"), d("100"), null));
    }

    @Test
    void atRiskWinsOverEverythingElse() {
        assertEquals(PartnerQualityBadge.AT_RISK, PartnerQualityBadgeRule.evaluate(50, d("5"), d("0"), d("0"), d("100"), d("5")));
        assertEquals(PartnerQualityBadge.AT_RISK, PartnerQualityBadgeRule.evaluate(50, d("0"), d("0"), d("20"), d("100"), d("5")));
        assertEquals(PartnerQualityBadge.AT_RISK, PartnerQualityBadgeRule.evaluate(1, d("100"), null, null, null, null));
    }

    @Test
    void risingNeedsThreeBookingsAndNoRiskSignal() {
        assertEquals(PartnerQualityBadge.RISING, PartnerQualityBadgeRule.evaluate(3, d("0"), d("0"), d("0"), null, null));
        assertEquals(PartnerQualityBadge.NONE, PartnerQualityBadgeRule.evaluate(2, d("0"), d("0"), d("0"), null, null));
        assertEquals(PartnerQualityBadge.NONE, PartnerQualityBadgeRule.evaluate(0, null, null, null, null, null));
    }
}
