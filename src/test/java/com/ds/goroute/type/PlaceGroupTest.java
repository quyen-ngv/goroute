package com.ds.goroute.type;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceGroupTest {

    @Test
    void legacySightseeingCodesReadAsAttractions() {
        for (String legacy : List.of("CULTURE_AND_HERITAGE", "NATURE_AND_OUTDOORS", "shopping_and_market", " OTHER ")) {
            assertThat(PlaceGroup.fromCode(legacy)).as(legacy).isEqualTo(PlaceGroup.ATTRACTIONS);
        }
        assertThat(PlaceGroup.fromCode("FOOD")).isEqualTo(PlaceGroup.FOOD_AND_DRINK);
        assertThat(PlaceGroup.fromCode("ACCOMMODATION")).isEqualTo(PlaceGroup.ACCOMMODATION);
        assertThat(PlaceGroup.fromCode("nonsense")).isNull();
        assertThat(PlaceGroup.fromCode(null)).isNull();
    }

    @Test
    void filtersFoldLegacyCodesButKeepUnknownOnesSoTheyMatchNothing() {
        assertThat(PlaceGroup.normalizeCodes(Arrays.asList("CULTURE_AND_HERITAGE", "ATTRACTIONS", "NATURE_AND_OUTDOORS")))
                .containsExactly("ATTRACTIONS");
        assertThat(PlaceGroup.normalizeCodes(List.of("bogus"))).containsExactly("BOGUS");
        assertThat(PlaceGroup.normalizeCodes(List.of(" "))).isNull();
        assertThat(PlaceGroup.normalizeCodes(null)).isNull();
    }

    @Test
    void oldAppRequestsStillBind() throws Exception {
        PlaceGroup[] groups = new ObjectMapper().readValue(
                "[\"CULTURE_AND_HERITAGE\",\"FOOD_AND_DRINK\"]", PlaceGroup[].class);

        assertThat(groups).containsExactly(PlaceGroup.ATTRACTIONS, PlaceGroup.FOOD_AND_DRINK);
    }
}
