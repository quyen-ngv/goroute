package com.ds.goroute.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceAiFactsTest {

    @Test
    void googlePriceRangesBecomeShortLabels() {
        assertThat(PlaceAiFacts.priceLabel("₫1–100,000", null)).isEqualTo("1-100k₫");
        assertThat(PlaceAiFacts.priceLabel("₫100,000–200,000", null)).isEqualTo("100k-200k₫");
        assertThat(PlaceAiFacts.priceLabel("₫1,000,000+", null)).isEqualTo("1M+₫");
        assertThat(PlaceAiFacts.priceLabel("$10–20", null)).isEqualTo("$10-20");
        assertThat(PlaceAiFacts.priceLabel("₫₫", null)).isEqualTo("₫₫");
    }

    @Test
    void enrichedPriceLevelIsTheFallback() {
        String attributes = "{\"price_level\":{\"value\":\"AFFORDABLE\",\"source_found\":true}}";
        assertThat(PlaceAiFacts.priceLabel(null, attributes)).isEqualTo("affordable");
        assertThat(PlaceAiFacts.priceLabel(" ", "not json")).isNull();
    }

    @Test
    void aboutKeepsOnlyWhitelistedEnabledOptions() {
        String about = """
                [{"id":"popular_for","options":[{"name":"Breakfast","enabled":true},{"name":"Solo dining","enabled":true}]},
                 {"id":"accessibility","options":[{"name":"Wheelchair accessible entrance","enabled":true}]},
                 {"id":"offerings","options":[{"name":"Vegetarian options","enabled":true},{"name":"Halal food","enabled":false}]},
                 {"id":"children","options":[{"name":"Good for kids","enabled":true},{"name":"High chairs","enabled":true}]},
                 {"id":"parking","options":[{"name":"Free parking lot","enabled":true}]}]
                """;
        assertThat(PlaceAiFacts.aboutTags(about))
                .containsExactly("breakfast", "solo", "wheelchair", "veg", "kids");
        assertThat(PlaceAiFacts.aboutTags(null)).isEmpty();
        assertThat(PlaceAiFacts.aboutTags("{\"broken\":")).isEmpty();
    }

    @Test
    void menuHighlightsSkipPhotoCaptionsAndDuplicates() {
        String menu = "{\"data\":[{\"title\":\"Bún bò\"}],\"highlights\":[{\"title\":\"Photo 1 of 4\"},"
                + "{\"title\":\"Bún bò\"},{\"title\":\"Chả cua\"}]}";
        assertThat(PlaceAiFacts.menuHighlights(menu, 6)).containsExactly("Bún bò", "Chả cua");
        assertThat(PlaceAiFacts.menuHighlights(menu, 1)).containsExactly("Bún bò");
    }
}
