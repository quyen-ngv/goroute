package com.ds.goroute.service.placelookup;

import com.ds.goroute.thirdparty.google.places.GooglePlace;
import com.ds.goroute.thirdparty.google.places.GooglePlacesClient;
import com.ds.goroute.thirdparty.google.places.GooglePlacesException;
import com.ds.goroute.thirdparty.google.places.GooglePlacesTextSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GooglePlaceLookupProviderTest {

    private GooglePlacesClient client;
    private GooglePlaceLookupProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(GooglePlacesClient.class);
        provider = new GooglePlaceLookupProvider(client);
    }

    @Test
    void mapsAPlaceToAHitWithItsGoogleId() {
        when(client.searchText(any())).thenReturn(List.of(place("ChIJ1", "Cộng Cà Phê", null, List.of("cafe", "food"))));

        List<PlaceHit> hits = provider.searchText(PlaceLookupQuery.text("Cong Ca Phe", 3));

        assertThat(hits).containsExactly(new PlaceHit(PlaceLookupProviderType.GOOGLE, "ChIJ1", "Cộng Cà Phê",
                "26 Ly Tu Trong", new BigDecimal("10.7772"), new BigDecimal("106.7012"), "cafe",
                "https://maps.google.com/?cid=1"));
    }

    @Test
    void sendsNoBiasForAPlainQueryAndCapsBiasAtGooglesLimit() {
        when(client.searchText(any())).thenReturn(List.of());
        ArgumentCaptor<GooglePlacesTextSearchRequest> request = ArgumentCaptor.forClass(GooglePlacesTextSearchRequest.class);

        provider.searchText(PlaceLookupQuery.text("Pho Hoa", 3));
        provider.searchText(new PlaceLookupQuery("Pho Hoa", new BigDecimal("10.7"), new BigDecimal("106.7"), 80_000, 50));

        verify(client, org.mockito.Mockito.times(2)).searchText(request.capture());
        assertThat(request.getAllValues().get(0).getLocationBias()).isNull();
        assertThat(request.getAllValues().get(0).getPageSize()).isEqualTo(3);
        assertThat(request.getAllValues().get(1).getLocationBias().getCircle().getRadius()).isEqualTo(50_000.0);
        assertThat(request.getAllValues().get(1).getPageSize()).isEqualTo(20);
    }

    @Test
    void wrapsClientFailures() {
        when(client.searchText(any())).thenThrow(new GooglePlacesException("Google Places text search answered 500"));

        assertThatThrownBy(() -> provider.searchText(PlaceLookupQuery.text("Pho Hoa", 3)))
                .isInstanceOf(PlaceLookupException.class)
                .hasMessage("Google Places text search answered 500");
    }

    private GooglePlace place(String id, String name, String primaryType, List<String> types) {
        GooglePlace place = new GooglePlace();
        place.setId(id);
        GooglePlace.LocalizedText displayName = new GooglePlace.LocalizedText();
        displayName.setText(name);
        place.setDisplayName(displayName);
        place.setFormattedAddress("26 Ly Tu Trong");
        GooglePlace.LatLng location = new GooglePlace.LatLng();
        location.setLatitude(new BigDecimal("10.7772"));
        location.setLongitude(new BigDecimal("106.7012"));
        place.setLocation(location);
        place.setPrimaryType(primaryType);
        place.setTypes(types);
        place.setGoogleMapsUri("https://maps.google.com/?cid=1");
        return place;
    }
}
