package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.ImportPlaceRequest;
import com.ds.goroute.entity.Place;
import com.ds.goroute.repository.FoodRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.PlaceReviewRepository;
import com.ds.goroute.repository.PlaceSourceRepository;
import com.ds.goroute.service.GeoService;
import com.ds.goroute.service.LocationAreaService;
import com.ds.goroute.service.PlaceSearchIndexService;
import com.ds.goroute.service.PlaceTranslationService;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceVisibilityStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A Google re-scrape and the fields a user suggestion locked (epic 09, plan §3.3). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlaceServiceImplLockedFieldsTest {

    @Mock private PlaceRepository placeRepository;
    @Mock private PlaceSourceRepository placeSourceRepository;
    @Mock private PlaceSearchIndexService placeSearchIndexService;
    @Mock private PlaceTranslationService placeTranslationService;
    @Mock private FoodRepository foodRepository;
    @Mock private PlaceReviewRepository placeReviewRepository;
    @Mock private GeoService geoService;
    @Mock private LocationAreaService locationAreaService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private PlaceServiceImpl service;

    @Test
    void lockedFieldsKeepTheStoredValueAndOthersTakeTheScrape() {
        Place existing = stored();
        when(placeRepository.findByPlaceId("g-1")).thenReturn(existing);
        when(placeRepository.findLockedFields(existing.getId()))
                .thenReturn(List.of("PHONE", "OPEN_HOURS", "BUSINESS_STATUS", "ADDRESS", "PRICE_RANGE"));

        service.importPlace(scrape(BigDecimal.TEN, BigDecimal.valueOf(106)));

        Place written = captureUpdate();
        assertThat(written.getPhone()).isEqualTo("024 1111");
        assertThat(written.getOpenHours()).isEqualTo("{\"Monday\":[\"7 AM–10 PM\"]}");
        assertThat(written.getRegular()).isEqualTo("{\"Monday\":[\"7 AM–10 PM\"]}");
        assertThat(written.getStatus()).isEqualTo("Permanently closed");
        assertThat(written.getAddress()).isEqualTo("13 Lò Đúc");
        assertThat(written.getPriceRange()).isEqualTo("₫50,000–100,000");
        // Not locked: Google's value wins as before.
        assertThat(written.getWebsite()).isEqualTo("https://google.example");
        assertThat(written.getTitle()).isEqualTo("Google title");
    }

    @Test
    void aLockedPinStaysWhereTheSuggestionPutIt() {
        Place existing = stored();
        when(placeRepository.findByPlaceId("g-1")).thenReturn(existing);
        when(placeRepository.findLockedFields(existing.getId())).thenReturn(List.of("LOCATION"));

        service.importPlace(scrape(BigDecimal.TEN, BigDecimal.valueOf(106)));

        Place written = captureUpdate();
        assertThat(written.getLatitude()).isEqualByComparingTo("21.0171");
        assertThat(written.getLongitude()).isEqualByComparingTo("105.8552");
        verify(placeRepository, never()).assignWard(existing.getId());
    }

    @Test
    void aScrapeWithoutCoordinatesNeverErasesTheStoredPoint() {
        Place existing = stored();
        when(placeRepository.findByPlaceId("g-1")).thenReturn(existing);

        service.importPlace(scrape(null, null));

        Place written = captureUpdate();
        assertThat(written.getLatitude()).isEqualByComparingTo("21.0171");
        assertThat(written.getLongitude()).isEqualByComparingTo("105.8552");
    }

    @Test
    void movedCoordinatesAreGivenTheirWardAgain() {
        Place existing = stored();
        when(placeRepository.findByPlaceId("g-1")).thenReturn(existing);

        service.importPlace(scrape(BigDecimal.TEN, BigDecimal.valueOf(106)));

        verify(placeRepository).assignWard(existing.getId());
    }

    private Place captureUpdate() {
        ArgumentCaptor<Place> captor = ArgumentCaptor.forClass(Place.class);
        verify(placeRepository).update(captor.capture());
        return captor.getValue();
    }

    private static ImportPlaceRequest scrape(BigDecimal latitude, BigDecimal longitude) {
        return ImportPlaceRequest.builder()
                .placeId("g-1")
                .title("Google title")
                .placeGroup("FOOD_AND_DRINK")
                .address("Google address")
                .phone("+84 999")
                .website("https://google.example")
                .openHours("{\"Monday\":[\"9 AM–5 PM\"]}")
                .regular("{\"Monday\":[\"9 AM–5 PM\"]}")
                .priceRange("₫1–2")
                .status("")
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }

    private static Place stored() {
        return Place.builder()
                .id(UUID.randomUUID())
                .placeId("g-1")
                .title("Phở Thìn")
                .placeGroup(PlaceGroup.FOOD_AND_DRINK)
                .subTypes(new String[] {"RESTAURANT"})
                .address("13 Lò Đúc")
                .phone("024 1111")
                .website("https://old.example")
                .openHours("{\"Monday\":[\"7 AM–10 PM\"]}")
                .regular("{\"Monday\":[\"7 AM–10 PM\"]}")
                .priceRange("₫50,000–100,000")
                .status("Permanently closed")
                .wardCode("00001")
                .latitude(new BigDecimal("21.0171"))
                .longitude(new BigDecimal("105.8552"))
                .visibilityStatus(PlaceVisibilityStatus.ACTIVE)
                .build();
    }
}
