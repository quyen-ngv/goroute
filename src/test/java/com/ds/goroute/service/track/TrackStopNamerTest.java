package com.ds.goroute.service.track;

import com.ds.goroute.dto.PlaceSearchCriteria;
import com.ds.goroute.entity.Place;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.PlaceSearchIndexService;
import com.ds.goroute.thirdparty.goong.GoongClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackStopNamerTest {

    private final PlaceSearchIndexService index = mock(PlaceSearchIndexService.class);
    private final PlaceRepository places = mock(PlaceRepository.class);
    private final GoongClient goong = mock(GoongClient.class);
    private final Executor inline = Runnable::run;
    private final TrackStopNamer namer = new TrackStopNamer(index, places, goong, new ObjectMapper(), inline);

    @Test
    void theNearestCataloguePlaceWithinTheRadiusWins() throws IOException {
        UUID near = UUID.randomUUID();
        UUID far = UUID.randomUUID();
        when(index.searchPlaceIds(any())).thenReturn(List.of(far, near));
        when(places.findByIds(List.of(far, near))).thenReturn(List.of(
                place(far, "Chợ", "11.9404", "108.4400"),
                place(near, "Cà phê Tùng", "11.9400", "108.4401")));

        assertThat(namer.nearestCatalogPlace(11.94, 108.44, 60))
                .contains(new TrackStopNamer.CatalogPlace(near, "Cà phê Tùng"));

        ArgumentCaptor<PlaceSearchCriteria> criteria = ArgumentCaptor.forClass(PlaceSearchCriteria.class);
        org.mockito.Mockito.verify(index).searchPlaceIds(criteria.capture());
        assertThat(criteria.getValue().radiusKm()).isEqualByComparingTo("0.06");
        assertThat(criteria.getValue().includeInactive()).isFalse();
    }

    @Test
    void anIndexFailureMeansNoCataloguePlace() throws IOException {
        when(index.searchPlaceIds(any())).thenThrow(new IOException("index closed"));

        assertThat(namer.nearestCatalogPlace(11.94, 108.44, 60)).isEmpty();
    }

    @Test
    void goongGivesTheFirstResultNameOrItsAddressAndNothingOnError() {
        when(goong.forward(eq("/Geocode"), any())).thenReturn(ResponseEntity.ok(
                "{\"results\":[{\"name\":\"\",\"formatted_address\":\"12 Nguyễn Chí Thanh, Đà Lạt\"}]}"));
        assertThat(namer.reverseGeocode(11.94, 108.44)).isEqualTo("12 Nguyễn Chí Thanh, Đà Lạt");

        when(goong.forward(eq("/Geocode"), any())).thenReturn(ResponseEntity.status(503).body("{}"));
        assertThat(namer.reverseGeocode(11.94, 108.44)).isNull();

        when(goong.forward(eq("/Geocode"), any())).thenThrow(new IllegalStateException("boom"));
        assertThat(namer.reverseGeocode(11.94, 108.44)).isNull();
    }

    private static Place place(UUID id, String title, String latitude, String longitude) {
        Place place = new Place();
        place.setId(id);
        place.setTitle(title);
        place.setLatitude(new BigDecimal(latitude));
        place.setLongitude(new BigDecimal(longitude));
        return place;
    }
}
