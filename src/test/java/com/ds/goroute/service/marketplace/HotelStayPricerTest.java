package com.ds.goroute.service.marketplace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class HotelStayPricerTest {

    @Test
    @DisplayName("Included taxes: service charge on the net price, VAT on net plus service charge")
    void includedTaxes() {
        // net 1,000,000 → +5% service = 1,050,000 → +8% VAT = 1,134,000
        assertThat(HotelStayPricer.includedTaxes(new BigDecimal("1134000"), new BigDecimal("8"), new BigDecimal("5")))
                .isEqualByComparingTo("134000");
        assertThat(HotelStayPricer.includedTaxes(new BigDecimal("1100000"), new BigDecimal("10"), null))
                .isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("No declared rate means no tax breakdown rather than zero")
    void noRatesMeansNull() {
        assertThat(HotelStayPricer.includedTaxes(new BigDecimal("1000000"), null, null)).isNull();
    }
}
