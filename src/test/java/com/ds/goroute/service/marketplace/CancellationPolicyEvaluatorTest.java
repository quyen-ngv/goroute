package com.ds.goroute.service.marketplace;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CancellationPolicyEvaluatorTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 10, 14, 0);
    private static final BigDecimal TOTAL = new BigDecimal("3000000");

    @Test
    void missingPolicyBehavesLikeFlexible24h() {
        var free = CancellationPolicyEvaluator.evaluate(null, START, START.minusHours(30), TOTAL, null);
        assertTrue(free.free());
        assertEquals(0, BigDecimal.ZERO.compareTo(free.penaltyAmount()));
        assertEquals(START.minusHours(24), free.freeUntil());

        var late = CancellationPolicyEvaluator.evaluate(Map.of(), START, START.minusHours(2), TOTAL, null);
        assertFalse(late.free());
        assertEquals(0, TOTAL.compareTo(late.penaltyAmount()));
        assertEquals("FULL_AMOUNT", late.penaltyRule());
    }

    @Test
    void nonRefundableIsNeverFree() {
        var outcome = CancellationPolicyEvaluator.evaluate(Map.of("type", "NON_REFUNDABLE"), START,
                START.minusDays(30), TOTAL, null);
        assertFalse(outcome.free());
        assertNull(outcome.freeUntil());
        assertEquals(0, TOTAL.compareTo(outcome.penaltyAmount()));
    }

    @Test
    void percentPenaltyAppliesAfterTheFreeWindow() {
        Map<String, Object> policy = new HashMap<>();
        policy.put("type", "CUSTOM");
        policy.put("freeCancellationHours", 48);
        policy.put("penaltyType", "PERCENT");
        policy.put("penaltyValue", 50);
        var outcome = CancellationPolicyEvaluator.evaluate(policy, START, START.minusHours(47), TOTAL, null);
        assertFalse(outcome.free());
        assertEquals(0, new BigDecimal("1500000").compareTo(outcome.penaltyAmount()));
        assertEquals("PERCENT_50", outcome.penaltyRule());
    }

    @Test
    void activityShorthandPenaltyPercentIsHonoured() {
        Map<String, Object> policy = Map.of("type", "FLEXIBLE", "freeCancellationHours", 24, "penaltyPercent", 30);
        var outcome = CancellationPolicyEvaluator.evaluate(policy, START, START.minusHours(1), TOTAL, null);
        assertEquals(0, new BigDecimal("900000").compareTo(outcome.penaltyAmount()));
    }

    @Test
    void firstNightPenaltyFallsBackToTotalWhenUnknown() {
        Map<String, Object> policy = Map.of("type", "STRICT", "penaltyType", "FIRST_NIGHT");
        var withFirst = CancellationPolicyEvaluator.evaluate(policy, START, START.minusDays(1), TOTAL, new BigDecimal("1000000"));
        assertEquals(0, new BigDecimal("1000000").compareTo(withFirst.penaltyAmount()));
        var withoutFirst = CancellationPolicyEvaluator.evaluate(policy, START, START.minusDays(1), TOTAL, null);
        assertEquals(0, TOTAL.compareTo(withoutFirst.penaltyAmount()));
        // STRICT defaults to 7 days of free cancellation
        assertEquals(START.minusDays(7), withFirst.freeUntil());
    }
}
