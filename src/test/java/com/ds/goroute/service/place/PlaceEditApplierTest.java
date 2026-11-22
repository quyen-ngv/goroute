package com.ds.goroute.service.place;

import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceEditGuard;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.PlaceTranslationService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.PlaceEditField;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceVisibilityStatus;
import com.ds.goroute.type.TranslationLocale;
import com.ds.goroute.type.TranslationSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Each field of plan §3.2: the right column, the right lock, the ward of a moved pin. */
class PlaceEditApplierTest {

    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final PlaceTranslationService translations = mock(PlaceTranslationService.class);
    private final BusinessConfigService config = mock(BusinessConfigService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final PlaceEditApplier applier = new PlaceEditApplier(placeRepository, translations, config, mapper);

    private Place place;
    private PlaceEditPlaceState state;

    @BeforeEach
    void setUp() {
        when(config.getInt(BusinessConfigKey.PLACE_EDIT_MAX_PIN_MOVE_METERS)).thenReturn(2000);
        when(config.getText(BusinessConfigKey.PLACE_EDIT_WEBSITE_BLOCKED_HOSTS)).thenReturn("bit.ly,tinyurl.com");
        place = Place.builder()
                .id(UUID.randomUUID())
                .title("Phở Thìn")
                .placeGroup(PlaceGroup.FOOD_AND_DRINK)
                .subTypes(new String[0])
                .latitude(new BigDecimal("21.0171"))
                .longitude(new BigDecimal("105.8552"))
                .wardCode("00001")
                .phone("024 1111")
                .status("")
                .attributes("{\"parking_available\":{\"value\":false}}")
                .visibilityStatus(PlaceVisibilityStatus.ACTIVE)
                .build();
        state = new PlaceEditPlaceState(place, List.of(), "vi");
    }

    @Test
    void phoneIsWrittenAndLocked() {
        ObjectNode value = applier.normalize(PlaceEditField.PHONE, json("{\"phone\":\" +84 24  3821 2709 \"}"), state, true);
        applier.apply(PlaceEditField.PHONE, value, state, "vi");

        verify(placeRepository).updatePhone(place.getId(), "+84 24 3821 2709");
        verify(placeRepository).addLockedFields(place.getId(), List.of("PHONE"));
    }

    @Test
    void clearingTheWebsiteWritesNullAndABlockedHostIsRefused() {
        ObjectNode cleared = applier.normalize(PlaceEditField.WEBSITE, json("{\"website\":null}"), state, true);
        applier.apply(PlaceEditField.WEBSITE, cleared, state, "vi");
        verify(placeRepository).updateWebsite(place.getId(), null);

        assertThatThrownBy(() -> applier.normalize(PlaceEditField.WEBSITE,
                json("{\"website\":\"https://bit.ly/abc\"}"), state, true)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> applier.normalize(PlaceEditField.WEBSITE,
                json("{\"website\":\"ftp://example.com\"}"), state, true)).isInstanceOf(BusinessException.class);
    }

    @Test
    void nameGoesToTheManualTranslationRowAndIsNotLocked() {
        ObjectNode value = applier.normalize(PlaceEditField.NAME, json("{\"name\":\"Phở Thìn Lò Đúc\"}"), state, true);
        applier.apply(PlaceEditField.NAME, value, state, "en");

        verify(translations).writeName(place.getId(), TranslationLocale.EN, "Phở Thìn Lò Đúc", TranslationSource.MANUAL);
        verify(placeRepository, never()).addLockedFields(any(), any());
    }

    @Test
    void descriptionKeepsTheShownNameForANewRow() {
        ObjectNode value = applier.normalize(PlaceEditField.DESCRIPTION,
                json("{\"description\":\"Quán phở bò lâu đời trên phố Lò Đúc.\"}"), state, true);
        applier.apply(PlaceEditField.DESCRIPTION, value, state, "vi");

        verify(translations).writeDescription(place.getId(), TranslationLocale.VI,
                "Quán phở bò lâu đời trên phố Lò Đúc.", "Phở Thìn", TranslationSource.MANUAL);
    }

    @Test
    void aMovedPinGetsItsWardAgainAndIsLocked() {
        when(placeRepository.assignWard(place.getId())).thenReturn(0);
        ObjectNode value = applier.normalize(PlaceEditField.LOCATION,
                json("{\"latitude\":21.0180,\"longitude\":105.8560}"), state, true);
        applier.apply(PlaceEditField.LOCATION, value, state, "vi");

        verify(placeRepository).updateLocation(eq(place.getId()), any(BigDecimal.class), any(BigDecimal.class));
        verify(placeRepository).assignWard(place.getId());
        // Outside every loaded boundary: the old ward is cleared rather than kept.
        verify(placeRepository).updateWardCode(place.getId(), null);
        verify(placeRepository).addLockedFields(place.getId(), List.of("LOCATION"));
    }

    @Test
    void aUserPinFartherThanTheCapIsRefusedButAnOperatorMayMoveIt() {
        JsonNode far = json("{\"latitude\":21.2,\"longitude\":105.9}");
        assertThatThrownBy(() -> applier.normalize(PlaceEditField.LOCATION, far, state, true))
                .isInstanceOf(BusinessException.class);
        assertThat(applier.normalize(PlaceEditField.LOCATION, far, state, false)).isNotNull();
    }

    @Test
    void openHoursStoreTheServerBuiltStringsInBothColumns() {
        StringBuilder week = new StringBuilder("{\"structured\":[");
        String[] days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};
        for (int i = 0; i < days.length; i++) {
            week.append(i == 0 ? "" : ",").append("{\"day\":\"").append(days[i]).append("\",")
                    .append(i == 0 ? "\"closed\":true,\"allDay\":false,\"ranges\":[]}"
                            : "\"closed\":false,\"allDay\":false,\"ranges\":[{\"from\":\"07:00\",\"to\":\"22:00\"}]}");
        }
        week.append("]}");

        ObjectNode value = applier.normalize(PlaceEditField.OPEN_HOURS, json(week.toString()), state, true);
        applier.apply(PlaceEditField.OPEN_HOURS, value, state, "vi");

        assertThat(value.get("display").get("Monday").get(0).asText()).isEqualTo("Closed");
        assertThat(value.get("display").get("Tuesday").get(0).asText()).isEqualTo("7 AM–10 PM");
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(placeRepository).updateOpenHours(eq(place.getId()), stored.capture());
        assertThat(stored.getValue()).contains("\"Tuesday\":[\"7 AM–10 PM\"]");
        verify(placeRepository).addLockedFields(place.getId(), List.of("OPEN_HOURS"));
    }

    @Test
    void priceRangeStoresItsDisplay() {
        ObjectNode value = applier.normalize(PlaceEditField.PRICE_RANGE,
                json("{\"min\":50000,\"max\":100000,\"currency\":\"VND\"}"), state, true);
        applier.apply(PlaceEditField.PRICE_RANGE, value, state, "vi");

        verify(placeRepository).updatePriceRange(place.getId(), "₫50,000–100,000");
        verify(placeRepository).addLockedFields(place.getId(), List.of("PRICE_RANGE"));
    }

    @Test
    void closuresLabelTheStatusAndAGonePlaceIsHidden() {
        applier.apply(PlaceEditField.BUSINESS_STATUS, applier.normalize(PlaceEditField.BUSINESS_STATUS,
                json("{\"reason\":\"PERMANENTLY_CLOSED\"}"), state, true), state, "vi");
        verify(placeRepository).updateStatusText(place.getId(), "Permanently closed");

        applier.apply(PlaceEditField.BUSINESS_STATUS, applier.normalize(PlaceEditField.BUSINESS_STATUS,
                json("{\"reason\":\"NOT_EXIST\"}"), state, true), state, "vi");
        verify(placeRepository).updateVisibility(place.getId(), PlaceVisibilityStatus.INACTIVE, null);
    }

    @Test
    void aDuplicatePointsAtAnActiveOriginal() {
        UUID original = UUID.randomUUID();
        when(placeRepository.findEditGuard(original)).thenReturn(Optional.of(PlaceEditGuard.builder()
                .id(original).visibilityStatus("ACTIVE").lifecycleStatus("ACTIVE").build()));

        ObjectNode value = applier.normalize(PlaceEditField.BUSINESS_STATUS,
                json("{\"reason\":\"DUPLICATE\",\"duplicateOfPlaceId\":\"" + original + "\"}"), state, true);
        applier.apply(PlaceEditField.BUSINESS_STATUS, value, state, "vi");

        verify(placeRepository).updateVisibility(place.getId(), PlaceVisibilityStatus.INACTIVE, original);
        verify(placeRepository).addLockedFields(place.getId(), List.of("BUSINESS_STATUS"));
    }

    @Test
    void reopeningNeedsAClosedPlace() {
        assertThatThrownBy(() -> applier.normalize(PlaceEditField.BUSINESS_STATUS,
                json("{\"reason\":\"REOPENED\"}"), state, true)).isInstanceOf(BusinessException.class);
    }

    @Test
    void categoryNeverMovesIntoAccommodationAndKindsMustFit() {
        assertThatThrownBy(() -> applier.normalize(PlaceEditField.CATEGORY,
                json("{\"placeGroup\":\"ACCOMMODATION\",\"subTypes\":[]}"), state, true))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> applier.normalize(PlaceEditField.CATEGORY,
                json("{\"placeGroup\":\"FOOD_AND_DRINK\",\"subTypes\":[\"BEACH\"]}"), state, true))
                .isInstanceOf(BusinessException.class);

        ObjectNode value = applier.normalize(PlaceEditField.CATEGORY,
                json("{\"placeGroup\":\"FOOD_AND_DRINK\",\"subTypes\":[\"CAFE\"]}"), state, true);
        applier.apply(PlaceEditField.CATEGORY, value, state, "vi");
        verify(placeRepository).updatePlaceGroup(place.getId(), PlaceGroup.FOOD_AND_DRINK);
        verify(placeRepository).updateSubTypes(place.getId(), new String[] {"CAFE"});
    }

    @Test
    void attributesAreOperatorOnlyAndMergeOnlyTheSentKeys() {
        JsonNode value = json("{\"attributes\":{\"air_conditioning\":{\"value\":true}}}");
        assertThatThrownBy(() -> applier.normalize(PlaceEditField.ATTRIBUTES, value, state, true))
                .isInstanceOf(BusinessException.class);

        ObjectNode normalized = applier.normalize(PlaceEditField.ATTRIBUTES, value, state, false);
        applier.apply(PlaceEditField.ATTRIBUTES, normalized, state, "vi");

        verify(placeRepository).updateAttributes(eq(place.getId()), anyString());
        verify(placeRepository, never()).addLockedFields(any(), any());
    }

    @Test
    void aSuggestionEqualToWhatThePlaceShowsIsUnchanged() {
        ObjectNode samePhone = applier.normalize(PlaceEditField.PHONE, json("{\"phone\":\"024 1111\"}"), state, true);
        ObjectNode nearPin = applier.normalize(PlaceEditField.LOCATION,
                json("{\"latitude\":21.01711,\"longitude\":105.85521}"), state, true);
        ObjectNode alias = applier.normalize(PlaceEditField.ALIAS, json("{\"alias\":\"phở thìn\"}"), state, true);

        assertThat(applier.isUnchanged(PlaceEditField.PHONE, samePhone, state)).isTrue();
        assertThat(applier.isUnchanged(PlaceEditField.LOCATION, nearPin, state)).isTrue();
        assertThat(applier.isUnchanged(PlaceEditField.ALIAS, alias, state)).isTrue();
    }

    private JsonNode json(String text) {
        try {
            return mapper.readTree(text);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
