package com.ds.goroute.service.marketplace;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromotionEngineTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 10, 1);
    private static final UUID RATE = UUID.randomUUID();

    private PromotionEngine.Promotion promo(String code, double percent, int priority, Integer minAdvance, Integer maxAdvance,
                                            Integer minNights, Set<DayOfWeek> days, UUID ratePlanId) {
        return new PromotionEngine.Promotion(UUID.randomUUID(), ratePlanId, code, code, "BASIC",
                BigDecimal.valueOf(percent), priority, null, null, null, null, minAdvance, maxAdvance, minNights, days, LocalDate.of(2026, 1, 1));
    }

    @Test
    void onlyOnePromotionAppliesAndItIsTheDeepestDiscount() {
        var small = promo("SMALL", 10, 1, null, null, null, null, null);
        var big = promo("BIG", 25, 900, null, null, null, null, null);
        var applied = PromotionEngine.price(List.of(small, big), RATE, CHECK_IN, new BigDecimal("1000000"), TODAY, CHECK_IN, 2);
        assertEquals("BIG", applied.code());
        // 25% off, not 35%: promotions never stack
        assertEquals(0, new BigDecimal("750000.00").compareTo(applied.price()));
        assertEquals(0, new BigDecimal("250000.00").compareTo(applied.discount()));
    }

    @Test
    void equalDiscountsAreBrokenByPriorityThenAge() {
        var lowPriority = promo("LOW", 20, 500, null, null, null, null, null);
        var highPriority = promo("HIGH", 20, 5, null, null, null, null, null);
        var best = PromotionEngine.bestFor(List.of(lowPriority, highPriority), RATE, CHECK_IN, TODAY, CHECK_IN, 2);
        assertEquals("HIGH", best.orElseThrow().code());
    }

    @Test
    void earlyBirdAndLastMinuteWindowsUseTheAdvanceDays() {
        var earlyBird = promo("EARLY", 15, 100, 30, null, null, null, null);   // 30+ days ahead
        var lastMinute = promo("LAST", 15, 100, null, 3, null, null, null);    // within 3 days
        // booking 30 days ahead: early bird applies, last minute does not
        assertTrue(PromotionEngine.bestFor(List.of(earlyBird), RATE, CHECK_IN, TODAY, CHECK_IN, 2).isPresent());
        assertTrue(PromotionEngine.bestFor(List.of(lastMinute), RATE, CHECK_IN, TODAY, CHECK_IN, 2).isEmpty());
        // booking the day before: the reverse
        LocalDate dayBefore = CHECK_IN.minusDays(1);
        assertTrue(PromotionEngine.bestFor(List.of(earlyBird), RATE, CHECK_IN, dayBefore, CHECK_IN, 2).isEmpty());
        assertTrue(PromotionEngine.bestFor(List.of(lastMinute), RATE, CHECK_IN, dayBefore, CHECK_IN, 2).isPresent());
    }

    @Test
    void weekdayMinimumNightsAndRatePlanNarrowTheMatch() {
        var weekend = promo("WEEKEND", 20, 100, null, null, null, Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), null);
        LocalDate thursday = LocalDate.of(2026, 10, 1);
        LocalDate friday = LocalDate.of(2026, 10, 2);
        assertTrue(PromotionEngine.bestFor(List.of(weekend), RATE, thursday, TODAY, thursday, 2).isEmpty());
        assertTrue(PromotionEngine.bestFor(List.of(weekend), RATE, friday, TODAY, thursday, 2).isPresent());

        var longStay = promo("LONG", 20, 100, null, null, 5, null, null);
        assertTrue(PromotionEngine.bestFor(List.of(longStay), RATE, CHECK_IN, TODAY, CHECK_IN, 4).isEmpty());
        assertTrue(PromotionEngine.bestFor(List.of(longStay), RATE, CHECK_IN, TODAY, CHECK_IN, 5).isPresent());

        var otherRate = promo("OTHER", 20, 100, null, null, null, null, UUID.randomUUID());
        assertTrue(PromotionEngine.bestFor(List.of(otherRate), RATE, CHECK_IN, TODAY, CHECK_IN, 2).isEmpty());
    }

    @Test
    void noPromotionLeavesThePriceUntouched() {
        var applied = PromotionEngine.price(List.of(), RATE, CHECK_IN, new BigDecimal("500000"), TODAY, CHECK_IN, 1);
        assertFalse(applied.discounted());
        assertEquals(0, new BigDecimal("500000").compareTo(applied.price()));
        assertEquals(0, BigDecimal.ZERO.compareTo(applied.discount()));
    }
}
