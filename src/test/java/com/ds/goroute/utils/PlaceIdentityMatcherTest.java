package com.ds.goroute.utils;

import com.ds.goroute.entity.Place;
import com.ds.goroute.repository.PlaceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceIdentityMatcherTest {

    private static final BigDecimal LAT = new BigDecimal("21.0285");
    private static final BigDecimal LNG = new BigDecimal("105.8542");
    private static final BigDecimal RADIUS = BigDecimal.valueOf(25);

    private final PlaceRepository placeRepository = mock(PlaceRepository.class);

    @Test
    void twoShopsSideBySideAreNotTheSamePlace() {
        Place cafe = candidate("Cộng Cà Phê", "12 Nhà Chung");
        when(placeRepository.findNearbyIdentityCandidates(LAT, LNG, RADIUS, PlaceIdentityMatcher.NEARBY_CANDIDATE_LIMIT))
                .thenReturn(List.of(cafe));

        Optional<Place> existing = PlaceIdentityMatcher.findExisting(placeRepository, null, null,
                LAT, LNG, "Phở Thìn", "14 Nhà Chung", RADIUS);

        assertThat(existing).isEmpty();
        verify(placeRepository, never()).findById(any());
    }

    @Test
    void sameNamedPlaceBehindANearerNeighbourIsFound() {
        Place nearestNeighbour = candidate("Highlands Coffee", "1 Tràng Tiền");
        Place sameShop = candidate("Phở Thìn - Bờ Hồ", "13 Lò Đúc");
        Place full = Place.builder().id(sameShop.getId()).title(sameShop.getTitle()).build();
        when(placeRepository.findNearbyIdentityCandidates(LAT, LNG, RADIUS, PlaceIdentityMatcher.NEARBY_CANDIDATE_LIMIT))
                .thenReturn(List.of(nearestNeighbour, sameShop));
        when(placeRepository.findById(sameShop.getId())).thenReturn(Optional.of(full));

        Optional<Place> existing = PlaceIdentityMatcher.findExisting(placeRepository, null, null,
                LAT, LNG, "phở thìn bờ hồ", null, RADIUS);

        assertThat(existing).contains(full);
    }

    @Test
    void googleIdWinsWithoutAnyRadiusQuery() {
        Place byGoogleId = candidate("Anything", null);
        when(placeRepository.findByPlaceId("ChIJ123")).thenReturn(byGoogleId);

        assertThat(PlaceIdentityMatcher.findExisting(placeRepository, "ChIJ123", "cid-1",
                LAT, LNG, "Other", null, RADIUS)).contains(byGoogleId);
        verify(placeRepository, never()).findNearbyIdentityCandidates(any(), any(), any(), anyInt());
    }

    @Test
    void anIncomingPlaceWithNoNameOrAddressIsNeverADuplicateByDistance() {
        assertThat(PlaceIdentityMatcher.findNearbySameIdentityId(placeRepository, LAT, LNG, " ", null, RADIUS))
                .isEmpty();
        verify(placeRepository, never()).findNearbyIdentityCandidates(any(), any(), any(), anyInt());
    }

    @Test
    void sameAddressCountsButAnEmptyOneNeverDoes() {
        assertThat(PlaceIdentityMatcher.sameIdentity("Old name", "12, Nhà Chung", "New name", "12 nhà chung"))
                .isTrue();
        assertThat(PlaceIdentityMatcher.sameIdentity("", "", "", "")).isFalse();
    }

    private static Place candidate(String title, String address) {
        return Place.builder().id(UUID.randomUUID()).title(title).address(address).build();
    }
}
