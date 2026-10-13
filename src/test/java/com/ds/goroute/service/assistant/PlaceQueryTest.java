package com.ds.goroute.service.assistant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceQueryTest {

    @Test
    void aKindAndFillerBecomeATypeFilterWithNoNameWords() {
        PlaceQuery query = PlaceQuery.of("quán cà phê dễ gặp gỡ bạn mới", null, null);

        assertThat(query.words()).isNull();
        assertThat(query.subTypes()).containsExactly("CAFE");
        assertThat(query.groups()).containsExactly("FOOD_AND_DRINK");
    }

    @Test
    void aDishStaysAsNameWordsBesideItsKind() {
        PlaceQuery query = PlaceQuery.of("quán bún chả cá ngon", null, null);

        assertThat(query.words()).isEqualTo("bún chả cá");
        assertThat(query.subTypes()).isNull();
    }

    @Test
    void askingForSomewhereToSleepSearchesAccommodationOnly() {
        PlaceQuery query = PlaceQuery.of("khách sạn gần biển Mỹ Khê", null, null);

        assertThat(query.words()).isEqualTo("mỹ khê");
        assertThat(query.subTypes()).isNull();
        assertThat(query.groups()).containsExactly("ACCOMMODATION");
        assertThat(PlaceQuery.of(null, List.of("BEACH"), "stay").groups()).containsExactly("ACCOMMODATION");
    }

    @Test
    void theLongestKindPhraseWins() {
        PlaceQuery query = PlaceQuery.of("quán board game", null, null);

        assertThat(query.words()).isNull();
        assertThat(query.subTypes()).containsExactly("ENTERTAINMENT");
        assertThat(query.groups()).containsExactly("ATTRACTIONS");
    }

    @Test
    void typesChosenByTheModelAreKeptAndKindWordsDropped() {
        PlaceQuery query = PlaceQuery.of("cafe sách", List.of("cafe", "nonsense"), "food");

        assertThat(query.words()).isEqualTo("sách");
        assertThat(query.subTypes()).containsExactly("CAFE");
        assertThat(query.groups()).containsExactly("FOOD_AND_DRINK");
    }

    @Test
    void kindsOfBothFamiliesLeaveTheGroupOpen() {
        PlaceQuery query = PlaceQuery.of("bảo tàng và cà phê", null, null);

        assertThat(query.subTypes()).containsExactlyInAnyOrder("MUSEUM", "CAFE");
        assertThat(query.groups()).isNull();
    }

    @Test
    void aPlainNameIsSearchedAsIs() {
        PlaceQuery query = PlaceQuery.of("Cộng", null, null);

        assertThat(query.words()).isEqualTo("cộng");
        assertThat(query.subTypes()).isNull();
        assertThat(query.groups()).isNull();
    }
}
