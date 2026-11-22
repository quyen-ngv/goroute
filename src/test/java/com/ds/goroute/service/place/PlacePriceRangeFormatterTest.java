package com.ds.goroute.service.place;

import com.ds.goroute.dto.PlacePriceRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PlacePriceRangeFormatter")
class PlacePriceRangeFormatterTest {

    @Test
    void formatsLikeTheScraperStoresGooglePrices() {
        assertThat(PlacePriceRangeFormatter.format(range(50_000, 100_000, "VND"))).isEqualTo("₫50,000–100,000");
        assertThat(PlacePriceRangeFormatter.format(range(30_000, 30_000, null))).isEqualTo("₫30,000");
    }

    @Test
    void refusesAnEmptyOrUpsideDownRangeOrAnotherCurrency() {
        assertThatThrownBy(() -> PlacePriceRangeFormatter.format(range(0, 10, "VND")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlacePriceRangeFormatter.format(range(200, 100, "VND")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlacePriceRangeFormatter.format(range(100, 200, "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsStoredAmountsBackAndIgnoresPriceLevels() {
        assertThat(PlacePriceRangeFormatter.parse("₫100,000–200,000 per person"))
                .isEqualTo(range(100_000, 200_000, "VND"));
        assertThat(PlacePriceRangeFormatter.parse("₫50,000")).isEqualTo(range(50_000, 50_000, "VND"));
        assertThat(PlacePriceRangeFormatter.parse("₫₫")).isNull();
        assertThat(PlacePriceRangeFormatter.parse("")).isNull();
    }

    private static PlacePriceRange range(long min, long max, String currency) {
        return PlacePriceRange.builder().min(min).max(max).currency(currency).build();
    }
}
