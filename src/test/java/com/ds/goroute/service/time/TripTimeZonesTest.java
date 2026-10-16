package com.ds.goroute.service.time;

import com.ds.goroute.entity.Trip;
import com.ds.goroute.mapper.TripMapper;
import com.ds.goroute.thirdparty.weather.OpenMeteoClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripTimeZonesTest {

    private static Trip trip(String zone, String lat, String lng) {
        return Trip.builder()
                .id(UUID.randomUUID())
                .timezone(zone)
                .destinationLat(lat == null ? null : new BigDecimal(lat))
                .destinationLng(lng == null ? null : new BigDecimal(lng))
                .build();
    }

    @Test
    void aStoredZoneWins() {
        assertThat(TripTimeZones.zoneIdOf(trip("Asia/Tokyo", "21.0278", "105.8342"))).isEqualTo("Asia/Tokyo");
    }

    @Test
    void anUnresolvedVietnameseDestinationReadsAsVietnam() {
        assertThat(TripTimeZones.zoneIdOf(trip(null, "10.7769", "106.7009"))).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    void anUnresolvedForeignOrMissingDestinationIsUnknown() {
        assertThat(TripTimeZones.zoneIdOf(trip(null, "35.6762", "139.6503"))).isNull();
        assertThat(TripTimeZones.zoneIdOf(trip(null, null, null))).isNull();
        assertThat(TripTimeZones.zoneIdOf(trip("not/a-zone", "35.6762", "139.6503"))).isNull();
        assertThat(TripTimeZones.zoneOf(trip(null, null, null)).getId()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    void pendingTripsAreLookedUpOncePerArea() {
        TripMapper mapper = mock(TripMapper.class);
        OpenMeteoClient client = mock(OpenMeteoClient.class);
        Trip tokyo = trip(null, "35.6762", "139.6503");
        Trip tokyoAgain = trip(null, "35.6801", "139.6498");
        when(mapper.selectWithoutTimezone(50)).thenReturn(List.of(tokyo, tokyoAgain));
        when(client.lookupTimeZone(any(), any())).thenReturn("Asia/Tokyo");
        when(mapper.updateTimezone(any(), any(), any(), any())).thenReturn(1);

        int resolved = new TripTimeZones(mapper, client).resolvePending(50);

        assertThat(resolved).isEqualTo(2);
        verify(client, times(1)).lookupTimeZone(any(), any());
        verify(mapper).updateTimezone(tokyo.getId(), "Asia/Tokyo", tokyo.getDestinationLat(), tokyo.getDestinationLng());
    }

    @Test
    void aFailedLookupLeavesTheTripForTheNextRun() {
        TripMapper mapper = mock(TripMapper.class);
        OpenMeteoClient client = mock(OpenMeteoClient.class);
        when(mapper.selectWithoutTimezone(50)).thenReturn(List.of(trip(null, "48.8566", "2.3522")));
        when(client.lookupTimeZone(any(), any())).thenThrow(new IllegalStateException("down"));

        assertThat(new TripTimeZones(mapper, client).resolvePending(50)).isZero();
        verify(mapper, times(0)).updateTimezone(any(), any(), any(), any());
    }
}
