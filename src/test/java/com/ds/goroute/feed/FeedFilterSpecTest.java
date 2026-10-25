package com.ds.goroute.feed;

import com.ds.goroute.dto.FeedGeoFilter;
import com.ds.goroute.dto.FeedPlaceKind;
import com.ds.goroute.type.FeedItemType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedFilterSpecTest {

    @Test
    void noChipIsTheWholeFeed() {
        FeedFilterSpec spec = FeedFilterSpec.of(null, null, null);

        assertThat(spec.itemTypes()).isEmpty();
        assertThat(spec.narrowed()).isFalse();
        assertThat(spec.tripsChip()).isFalse();
        assertThat(spec.singleType()).isFalse();
    }

    @Test
    void theTripsChipIsTripsOnlyWithoutTheGate() {
        FeedFilterSpec spec = FeedFilterSpec.of(List.of(FeedItemType.TRIP, FeedItemType.TRIP), null, null);

        assertThat(spec.itemTypes()).containsExactly(FeedItemType.TRIP);
        assertThat(spec.tripsChip()).isTrue();
        assertThat(spec.singleType()).isTrue();
        assertThat(spec.narrowed()).isTrue();
    }

    @Test
    void aPlaceChipIsNotTheTripsChipEvenWithTripsAsked() {
        FeedFilterSpec spec = FeedFilterSpec.of(List.of(FeedItemType.TRIP), FeedPlaceKind.of(List.of("ATTRACTIONS"), null), null);

        assertThat(spec.tripsChip()).isFalse();
        assertThat(spec.hasPlaceKind()).isTrue();
    }

    @Test
    void differentChipsAreDifferentSessions() {
        FeedFilterSpec all = FeedFilterSpec.of(List.of(), null, null);
        FeedFilterSpec cafes = FeedFilterSpec.of(List.of(), FeedPlaceKind.of(null, List.of("CAFE")), null);
        FeedFilterSpec near = FeedFilterSpec.of(List.of(), null,
                FeedGeoFilter.of(BigDecimal.valueOf(21.0285), BigDecimal.valueOf(105.8542), BigDecimal.valueOf(50)));
        FeedFilterSpec nearAgain = FeedFilterSpec.of(List.of(), null,
                FeedGeoFilter.of(BigDecimal.valueOf(21.0285), BigDecimal.valueOf(105.8542), BigDecimal.valueOf(50)));

        assertThat(all.key()).isNotEqualTo(cafes.key()).isNotEqualTo(near.key());
        assertThat(near.key()).isEqualTo(nearAgain.key());
    }
}
