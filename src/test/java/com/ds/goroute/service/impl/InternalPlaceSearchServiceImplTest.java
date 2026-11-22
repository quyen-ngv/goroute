package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.InternalPlaceSearchRequest;
import com.ds.goroute.dto.response.InternalPlaceSearchResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.PlaceSearchIndexService;
import com.ds.goroute.service.placelookup.PlaceHit;
import com.ds.goroute.service.placelookup.PlaceLookupException;
import com.ds.goroute.service.placelookup.PlaceLookupProvider;
import com.ds.goroute.service.placelookup.PlaceLookupProviderType;
import com.ds.goroute.service.placelookup.PlaceLookupProviders;
import com.ds.goroute.service.placelookup.PlaceLookupQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalPlaceSearchServiceImplTest {

    private PlaceSearchIndexService index;
    private PlaceRepository places;
    private PlaceLookupProvider mapbox;
    private InternalPlaceSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        index = mock(PlaceSearchIndexService.class);
        places = mock(PlaceRepository.class);
        mapbox = mock(PlaceLookupProvider.class);
        when(mapbox.type()).thenReturn(PlaceLookupProviderType.MAPBOX);
        service = new InternalPlaceSearchServiceImpl(index, places, new PlaceLookupProviders(List.of(mapbox)));
    }

    @Test
    void answersFromTheCatalogueWithoutAskingMapbox() throws Exception {
        UUID id = UUID.randomUUID();
        when(index.searchPlaceIdsByName(eq("Hồ Gươm"), isNull(), eq(false), eq(0), anyInt())).thenReturn(List.of(id));
        when(places.findByIds(List.of(id))).thenReturn(List.of(place(id, "Hồ Gươm (Hồ Hoàn Kiếm)")));

        InternalPlaceSearchResponse response = service.search(request("Hồ Gươm Hà Nội", "Hồ Gươm"));

        assertThat(response.getProvider()).isEqualTo("CATALOG");
        assertThat(response.getCandidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.getCatalogPlaceId()).isEqualTo(id.toString());
            assertThat(candidate.getPlaceId()).isEqualTo("ChIJ1");
            assertThat(candidate.getMapboxId()).isNull();
        });
        verify(mapbox, never()).searchText(any());
    }

    @Test
    void asksMapboxWithTheFullQueryWhenNoCataloguePlaceHasTheName() throws Exception {
        UUID id = UUID.randomUUID();
        when(index.searchPlaceIdsByName(anyString(), isNull(), eq(false), eq(0), anyInt())).thenReturn(List.of(id));
        // Shares one word with the asked name only.
        when(places.findByIds(List.of(id))).thenReturn(List.of(place(id, "Chùa Một Cột")));
        when(mapbox.isAvailable()).thenReturn(true);
        when(mapbox.searchText(PlaceLookupQuery.text("Chùa Cầu Hội An", 3))).thenReturn(List.of(
                new PlaceHit(PlaceLookupProviderType.MAPBOX, "dXJuOm1ieHBvaTox", "Chùa Cầu", "Nguyễn Thị Minh Khai",
                        new BigDecimal("15.8772"), new BigDecimal("108.3260"), "temple", null)));

        InternalPlaceSearchResponse response = service.search(request("Chùa Cầu Hội An", "Chùa Cầu"));

        assertThat(response.getProvider()).isEqualTo("MAPBOX");
        assertThat(response.getCandidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.getMapboxId()).isEqualTo("dXJuOm1ieHBvaTox");
            assertThat(candidate.getCatalogPlaceId()).isNull();
            assertThat(candidate.getPlaceId()).isNull();
            assertThat(candidate.getLatitude()).isEqualByComparingTo("15.8772");
        });
    }

    @Test
    void refusesWithServiceUnavailableWhenMapboxIsNotConfigured() {
        when(mapbox.isAvailable()).thenReturn(false);

        assertThatThrownBy(() -> service.search(request("Pho Hoa", null)))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getError().getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(mapbox, never()).searchText(any());
    }

    @Test
    void reportsBadGatewayWhenMapboxFails() {
        when(mapbox.isAvailable()).thenReturn(true);
        when(mapbox.searchText(any())).thenThrow(new PlaceLookupException("Mapbox search failed: 500"));

        assertThatThrownBy(() -> service.search(request("Pho Hoa", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getError().getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getMessage()).isEqualTo("Mapbox place search failed");
                });
    }

    private static Place place(UUID id, String title) {
        return Place.builder().id(id).title(title).placeId("ChIJ1")
                .latitude(new BigDecimal("21.0287")).longitude(new BigDecimal("105.8524")).build();
    }

    private static InternalPlaceSearchRequest request(String query, String name) {
        return InternalPlaceSearchRequest.builder().query(query).name(name).limit(3).build();
    }
}
