package com.ds.goroute.type;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaceSubTypeTest {

    @Test
    void filterCodesAcceptOtherBesideTheKinds() {
        assertThat(PlaceSubType.filterCodes(List.of(" halal", "other", "HALAL", "")))
                .containsExactly("HALAL", PlaceSubType.UNTYPED_FILTER);
        assertThat(PlaceSubType.filterCodes(List.of(" "))).isNull();
        assertThat(PlaceSubType.filterCodes(null)).isNull();
    }

    @Test
    void anUnknownFilterCodeIsRejected() {
        assertThatThrownBy(() -> PlaceSubType.filterCodes(List.of("PIZZA")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void otherIsNeverStored() {
        assertThat(PlaceSubType.toStorable(List.of("OTHER"), PlaceGroup.ATTRACTIONS)).isEmpty();
    }

    @Test
    void fittingKindsFollowTheGroups() {
        assertThat(PlaceSubType.fittingAny(List.of(PlaceGroup.FOOD_AND_DRINK)))
                .allMatch(kind -> kind.getFamily() == PlaceSubType.Family.FOOD)
                .contains(PlaceSubType.HALAL);
        assertThat(PlaceSubType.fittingAny(List.of())).hasSize(PlaceSubType.values().length);
    }

    @Test
    void foodKindsFitOnlyFoodPlaces() {
        assertThat(PlaceSubType.HALAL.fits(PlaceGroup.FOOD_AND_DRINK)).isTrue();
        assertThat(PlaceSubType.HALAL.fits(PlaceGroup.ATTRACTIONS)).isFalse();
        assertThat(PlaceSubType.HALAL.fits(PlaceGroup.ACCOMMODATION)).isFalse();
    }

    @Test
    void attractionKindsFitAttractionsButNotFoodOrStays() {
        assertThat(PlaceSubType.BEACH.fits(PlaceGroup.ATTRACTIONS)).isTrue();
        assertThat(PlaceSubType.BEACH.fits(PlaceGroup.FOOD_AND_DRINK)).isFalse();
        assertThat(PlaceSubType.BEACH.fits(PlaceGroup.ACCOMMODATION)).isFalse();
    }

    @Test
    void everyKindFitsAPlaceWithoutAGroup() {
        assertThat(Arrays.stream(PlaceSubType.values()).allMatch(kind -> kind.fits(null))).isTrue();
    }

    @Test
    void storableFormDropsUnknownAndMisfitCodesAndKeepsDeclarationOrder() {
        String[] stored = PlaceSubType.toStorable(
                List.of("halal", " seafood ", "BEACH", "not-a-kind", "HALAL", ""), PlaceGroup.FOOD_AND_DRINK);

        assertThat(stored).containsExactly("SEAFOOD", "HALAL");
    }

    @Test
    void storableFormOfNothingIsAnEmptyArray() {
        assertThat(PlaceSubType.toStorable(null, PlaceGroup.ATTRACTIONS)).isEmpty();
        assertThat(PlaceSubType.toStorable(List.of(), PlaceGroup.ATTRACTIONS)).isEmpty();
    }

    @Test
    void filterNamesAreNullWhenNothingWasAskedFor() {
        assertThat(PlaceSubType.names(null)).isNull();
        assertThat(PlaceSubType.names(List.of())).isNull();
        assertThat(PlaceSubType.names(List.of(PlaceSubType.CAFE, PlaceSubType.CAFE, PlaceSubType.TEA)))
                .containsExactly("CAFE", "TEA");
    }
}
