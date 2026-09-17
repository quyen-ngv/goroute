package com.ds.goroute.service.marketplace;

import com.ds.goroute.service.ExchangeRateService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * No request header is installed, so the target currency is the filter default
 * (USD) — enough to exercise every branch.
 */
class MarketplaceDisplayPriceTest {

    private static MarketplaceDisplayPrice withRate(BigDecimal rate) {
        return new MarketplaceDisplayPrice(new ExchangeRateService() {
            @Override public BigDecimal convert(BigDecimal amount, String from, String to) {
                throw new AssertionError("conversion must go through getRate");
            }

            @Override public BigDecimal getRate(String from, String to) {
                return rate;
            }
        });
    }

    @Test
    void convertsAndRelabelsWhenARateExists() {
        var display = withRate(new BigDecimal("0.00004"));
        assertEquals(new BigDecimal("32.00"), display.convert(new BigDecimal("800000"), "VND"));
        assertEquals("USD", display.currencyOf("VND"));
    }

    @Test
    void leavesTheAmountAloneWhenItIsAlreadyInTheTargetCurrency() {
        var display = withRate(new BigDecimal("0.00004"));
        assertEquals(new BigDecimal("32"), display.convert(new BigDecimal("32"), "usd"));
        assertEquals("USD", display.currencyOf("usd"));
    }

    @Test
    void keepsTheStoredCurrencyWhenTheRateProviderIsDown() {
        // getRate reports an outage as a rate of one; publishing dong under a
        // dollar sign would be worse than publishing dong.
        var display = withRate(BigDecimal.ONE);
        assertEquals(new BigDecimal("800000"), display.convert(new BigDecimal("800000"), "VND"));
        assertEquals("VND", display.currencyOf("VND"));
    }

    @Test
    void anAbsentPriceStaysAbsent() {
        assertNull(withRate(new BigDecimal("0.00004")).convert((BigDecimal) null, "VND"));
    }

    @Test
    void convertsEveryEntryOfAUnitPriceMap() {
        var display = withRate(new BigDecimal("0.00004"));
        var converted = display.convert(
                Map.of("ADULT", new BigDecimal("500000"), "CHILD", new BigDecimal("250000")), "VND");
        assertEquals(new BigDecimal("20.00"), converted.get("ADULT"));
        assertEquals(new BigDecimal("10.00"), converted.get("CHILD"));
    }
}
