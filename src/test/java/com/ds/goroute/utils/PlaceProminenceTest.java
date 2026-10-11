package com.ds.goroute.utils;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceProminenceTest {

    @Test
    void aPlaceTravellersUseOutranksOneWithManyGoogleReviews() {
        double used = PlaceProminence.of(UUID.randomUUID(), 1, 0, 0);
        double reviewed = PlaceProminence.of(UUID.randomUUID(), 0, 0, 1_000_000);

        assertThat(used).isGreaterThan(reviewed);
    }

    @Test
    void tripsAndCheckinsBothCount() {
        UUID id = UUID.randomUUID();

        assertThat(PlaceProminence.of(id, 3, 2, 0)).isGreaterThan(PlaceProminence.of(id, 3, 0, 0));
        assertThat(PlaceProminence.of(id, 3, 2, 0)).isGreaterThan(PlaceProminence.of(id, 0, 2, 0));
    }

    @Test
    void reviewCountOrdersPlacesNobodyUsedYet() {
        UUID id = UUID.randomUUID();

        assertThat(PlaceProminence.of(id, 0, 0, 20_000)).isGreaterThan(PlaceProminence.of(id, 0, 0, 3));
    }

    @Test
    void placesWithoutAnyUseOrReviewStillGetDistinctScores() {
        double first = PlaceProminence.of(UUID.randomUUID(), null, null, null);
        double second = PlaceProminence.of(UUID.randomUUID(), null, null, null);

        assertThat(first).isNotEqualTo(second);
        assertThat(first).isBetween(0.0, 1e-6);
        assertThat(PlaceProminence.of(null, null, null, null)).isZero();
    }
}
