package com.ds.goroute.config.cache;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SearchCacheKeyGeneratorTest {

    private final SearchCacheKeyGenerator placeKeys = new SearchCacheKeyGenerator(Set.of(1, 2));
    private final Method method = Object.class.getMethods()[0];

    @Test
    void pointsAFewMetresApartShareOneKey() {
        Object first = placeKeys.generate(this, method, "pho", new BigDecimal("21.028112"), new BigDecimal("105.854201"), BigDecimal.ONE);
        Object second = placeKeys.generate(this, method, "pho", new BigDecimal("21.02809"), new BigDecimal("105.85418"), BigDecimal.ONE);

        assertThat(first).isEqualTo(second);
        assertThat(first.toString()).contains("|21.0281|105.8542|");
    }

    @Test
    void pointsFiftyMetresApartDoNot() {
        Object first = placeKeys.generate(this, method, null, new BigDecimal("21.0281"), new BigDecimal("105.854"), BigDecimal.ONE);
        Object second = placeKeys.generate(this, method, null, new BigDecimal("21.0286"), new BigDecimal("105.854"), BigDecimal.ONE);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void pointsAKilometreApartDoNot() {
        Object first = placeKeys.generate(this, method, null, new BigDecimal("21.028"), new BigDecimal("105.854"), BigDecimal.ONE);
        Object second = placeKeys.generate(this, method, null, new BigDecimal("21.038"), new BigDecimal("105.854"), BigDecimal.ONE);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void onlyTheNamedPositionsAreRounded() {
        Object key = placeKeys.generate(this, method, null, new BigDecimal("21.0285"), new BigDecimal("105.8542"),
                new BigDecimal("0.12345"));

        assertThat(key.toString()).endsWith("|0.12345");
        assertThat(new SearchCacheKeyGenerator().generate(this, method, new BigDecimal("21.02855")).toString())
                .endsWith("|21.02855");
    }
}
