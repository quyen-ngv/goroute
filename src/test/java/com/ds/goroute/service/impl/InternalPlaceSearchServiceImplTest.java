package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.InternalPlaceSearchRequest;
import com.ds.goroute.dto.response.InternalPlaceSearchResponse;
import com.ds.goroute.exception.BusinessException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalPlaceSearchServiceImplTest {

    private PlaceLookupProvider google;
    private InternalPlaceSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        google = mock(PlaceLookupProvider.class);
        when(google.type()).thenReturn(PlaceLookupProviderType.GOOGLE);
        service = new InternalPlaceSearchServiceImpl(new PlaceLookupProviders(List.of(google)));
    }

    @Test
    void answersInTheScrapeWorkersCandidateShape() {
        when(google.isAvailable()).thenReturn(true);
        when(google.searchText(new PlaceLookupQuery("Cong Ca Phe", null, null, null, 3))).thenReturn(List.of(
                new PlaceHit(PlaceLookupProviderType.GOOGLE, "ChIJ1", "Cộng Cà Phê", "26 Ly Tu Trong",
                        new BigDecimal("10.7772"), new BigDecimal("106.7012"), "cafe",
                        "https://maps.google.com/?cid=17393364373045178667"),
                new PlaceHit(PlaceLookupProviderType.GOOGLE, "ChIJ2", "Cộng Cà Phê 2", null,
                        null, null, null, null)));

        InternalPlaceSearchResponse response = service.search(request("  Cong Ca Phe ", 3));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getQuery()).isEqualTo("Cong Ca Phe");
        assertThat(response.getProvider()).isEqualTo("GOOGLE");
        assertThat(response.getCount()).isEqualTo(2);
        assertThat(response.getCandidates().get(0)).isEqualTo(InternalPlaceSearchResponse.Candidate.builder()
                .rank(1)
                .title("Cộng Cà Phê")
                .googleMapsLink("https://maps.google.com/?cid=17393364373045178667")
                .resolvedUrl("https://maps.google.com/?cid=17393364373045178667")
                .placeId("ChIJ1")
                .cid("17393364373045178667")
                .latitude(new BigDecimal("10.7772"))
                .longitude(new BigDecimal("106.7012"))
                .address("26 Ly Tu Trong")
                .category("cafe")
                .build());
        assertThat(response.getCandidates().get(1).getRank()).isEqualTo(2);
        assertThat(response.getCandidates().get(1).getCid()).isNull();
    }

    @Test
    void refusesWithServiceUnavailableWhenThereIsNoKey() {
        when(google.isAvailable()).thenReturn(false);

        assertThatThrownBy(() -> service.search(request("Pho Hoa", 3)))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getError().getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(google, never()).searchText(any());
    }

    @Test
    void reportsBadGatewayWithoutGooglesMessageWhenGoogleFails() {
        when(google.isAvailable()).thenReturn(true);
        when(google.searchText(any())).thenThrow(new PlaceLookupException("Google Places text search answered 500"));

        assertThatThrownBy(() -> service.search(request("Pho Hoa", 3)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getError().getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getMessage()).isEqualTo("Google place search failed");
                });
    }

    private InternalPlaceSearchRequest request(String query, int limit) {
        return InternalPlaceSearchRequest.builder().query(query).limit(limit).build();
    }
}
