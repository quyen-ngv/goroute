package com.ds.goroute.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CitySlugResolverTest {

    @Test
    @DisplayName("The TP.HCM filter matches places tagged with any alias, including the imported 'hcm'")
    void hcmcFilterListsAliases() {
        String filter = CitySlugResolver.toJsonbFilter("hcmc");

        assertThat(filter).startsWith("[\"hcmc\"").contains("\"hcm\"").contains("\"saigon\"");
        assertThat(CitySlugResolver.toJsonbFilter("hcm")).isEqualTo(filter);
    }

    @Test
    @DisplayName("A city without aliases filters on its own slug only")
    void unknownCityFiltersOnItsSlug() {
        assertThat(CitySlugResolver.toJsonbFilter("dalat")).isEqualTo("[\"dalat\"]");
    }
}
