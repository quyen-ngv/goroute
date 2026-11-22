package com.ds.goroute.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedPlaceKindTest {

    @Test
    void noChipIsNoFilter() {
        assertThat(FeedPlaceKind.of(null, null)).isNull();
        assertThat(FeedPlaceKind.of(List.of(), Arrays.asList(" ", null))).isNull();
    }

    @Test
    void codesAreReadAsTheAppSendsThem() {
        FeedPlaceKind kind = FeedPlaceKind.of(List.of("attractions", "ATTRACTIONS"), List.of(" cafe "));

        assertThat(kind.getPlaceGroups()).containsExactly("ATTRACTIONS");
        assertThat(kind.getSubTypes()).containsExactly("CAFE");
    }

    @Test
    void anOldGroupCodeFoldsIntoTheGroupItBecame() {
        assertThat(FeedPlaceKind.of(List.of("NATURE_AND_OUTDOORS"), null).getPlaceGroups())
                .containsExactly("ATTRACTIONS");
    }

    @Test
    void anUnknownCodeIsRefusedRatherThanMatchingNothingQuietly() {
        assertThatThrownBy(() -> FeedPlaceKind.of(List.of("SPACESHIPS"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SPACESHIPS");
        // "OTHER" means "no sub type" to the place search; a feed chip never sends it.
        assertThatThrownBy(() -> FeedPlaceKind.of(null, List.of("OTHER")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
