package com.ds.goroute.service.track;

import com.ds.goroute.dto.TrackPointsPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackPointsCodecTest {

    private static final long T0 = 1_759_374_000_000L;

    @Test
    void decodesCumulativeTimesAndScaledCoordinates() {
        TrackPointsPayload payload = TrackPointsPayload.builder()
                .v(1)
                .t0(T0)
                .dt(List.of(0L, 5_000L, 4_000L))
                .lat(List.of(119_414_560L, 119_414_602L, 119_414_700L))
                .lng(List.of(1_084_401_230L, 1_084_401_300L, 1_084_401_400L))
                .acc(Arrays.asList(6, null, 8))
                .alt(Arrays.asList(1502, 1503, null))
                .spd(null)
                .build();

        List<TrackPoint> points = TrackPointsCodec.decode(payload, 500);

        assertThat(points).extracting(TrackPoint::timeMs).containsExactly(T0, T0 + 5_000, T0 + 9_000);
        assertThat(points.get(0).latitude()).isEqualTo(11.941456);
        assertThat(points.get(0).longitude()).isEqualTo(108.440123);
        assertThat(points.get(1).accuracyM()).isNull();
        assertThat(points.get(2).speedDms()).isNull();
    }

    @Test
    void roundTripsThroughJsonUnchanged() throws Exception {
        List<TrackPoint> points = List.of(
                new TrackPoint(T0, 11.941456, 108.440123, 6, 1502, 13),
                new TrackPoint(T0 + 5_021, 11.9414602, 108.44013, null, null, null));
        ObjectMapper mapper = new ObjectMapper();

        String json = mapper.writeValueAsString(TrackPointsCodec.encode(points));
        List<TrackPoint> decoded = TrackPointsCodec.decode(mapper.readValue(json, TrackPointsPayload.class), 500);

        assertThat(decoded).isEqualTo(points);
    }

    @Test
    void refusesTimeThatDoesNotIncrease() {
        TrackPointsPayload payload = basic(List.of(0L, 0L));

        assertThatThrownBy(() -> TrackPointsCodec.decode(payload, 500))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly increase");
    }

    @Test
    void refusesMismatchedArraysAndOversizedChunks() {
        TrackPointsPayload mismatched = basic(List.of(0L, 1_000L));
        mismatched.setLng(List.of(1L));
        assertThatThrownBy(() -> TrackPointsCodec.decode(mismatched, 500))
                .hasMessageContaining("lng has 1 values");

        assertThatThrownBy(() -> TrackPointsCodec.decode(basic(List.of(0L, 1_000L, 2_000L)), 2))
                .hasMessageContaining("too many points");
    }

    @Test
    void refusesNonZeroFirstStepAndUnknownFormat() {
        assertThatThrownBy(() -> TrackPointsCodec.decode(basic(List.of(5L, 1_000L)), 500))
                .hasMessageContaining("dt[0] must be 0");

        TrackPointsPayload future = basic(List.of(0L, 1_000L));
        future.setV(2);
        assertThatThrownBy(() -> TrackPointsCodec.decode(future, 500))
                .hasMessageContaining("unsupported points format");
    }

    private static TrackPointsPayload basic(List<Long> dt) {
        List<Long> coordinates = dt.stream().map(ignored -> 100_000_000L).toList();
        return TrackPointsPayload.builder()
                .v(1)
                .t0(T0)
                .dt(dt)
                .lat(coordinates)
                .lng(coordinates)
                .build();
    }
}
