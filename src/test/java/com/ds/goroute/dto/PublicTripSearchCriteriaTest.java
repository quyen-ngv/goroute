package com.ds.goroute.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PublicTripSearchCriteriaTest {

    @Test
    void nearbyPointsShareOneRankingKey() {
        PublicTripSearchCriteria first = PublicTripSearchCriteria.of(new BigDecimal("21.02851"),
                new BigDecimal("105.85419"), new BigDecimal("1000"), null, null, false, null, false, false);
        PublicTripSearchCriteria second = PublicTripSearchCriteria.of(new BigDecimal("21.0301"),
                new BigDecimal("105.8499"), new BigDecimal("1000.0"), null, null, false, null, false, false);

        assertThat(first).isEqualTo(second);
        assertThat(first.latitude()).isEqualTo(21.03);
        assertThat(first.longitude()).isEqualTo(105.85);
        assertThat(first.radiusMeters()).isEqualTo(1_000_000d);
        assertThat(first.orderedByDistance()).isTrue();
    }

    @Test
    void allPublicDropsThePointAndRadiusSoEveryoneSharesIt() {
        PublicTripSearchCriteria hanoi = PublicTripSearchCriteria.of(new BigDecimal("21.03"),
                new BigDecimal("105.85"), new BigDecimal("50"), null, null, true, null, true, true);
        PublicTripSearchCriteria saigon = PublicTripSearchCriteria.of(new BigDecimal("10.78"),
                new BigDecimal("106.70"), new BigDecimal("1000"), null, null, true, null, true, true);

        assertThat(hanoi).isEqualTo(saigon);
        assertThat(hanoi.latitude()).isNull();
        assertThat(hanoi.radiusMeters()).isNull();
        assertThat(hanoi.orderedByDistance()).isFalse();
    }

    @Test
    void recommendedIgnoresTheRandomSeed() {
        PublicTripSearchCriteria criteria = PublicTripSearchCriteria.of(null, null, null, null, null,
                true, "abc", false, true);

        assertThat(criteria.randomSeed()).isNull();
    }

    @Test
    void textFiltersAreTrimmedLowerCasedAndBlankMeansNone() {
        PublicTripSearchCriteria criteria = PublicTripSearchCriteria.of(null, null, null, "  Đà LẠT ", "   ",
                true, " ", false, false);

        assertThat(criteria.destination()).isEqualTo("đà lạt");
        assertThat(criteria.keyword()).isNull();
        assertThat(criteria.randomSeed()).isNull();
    }

    @Test
    void aMissingRadiusMatchesNothingRatherThanEverything() {
        PublicTripSearchCriteria criteria = PublicTripSearchCriteria.of(new BigDecimal("21"),
                new BigDecimal("105"), null, null, null, false, null, false, false);

        assertThat(criteria.radiusMeters()).isZero();
    }
}
