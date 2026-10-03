package com.ds.goroute.service.track;

import com.ds.goroute.type.TrackSegmentKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrackRouteWktTest {

    @Test
    void tracksAndGapsBecomeLinesWithLongitudeFirstAndFlightsAreLeftOut() {
        List<TrackSegmentLine> segments = List.of(
                line(TrackSegmentKind.TRACK, new double[] {11.94, 108.44}, new double[] {11.95, 108.45}),
                line(TrackSegmentKind.GAP, new double[] {11.95, 108.45}, new double[] {12.2, 108.6}),
                line(TrackSegmentKind.FLIGHT, new double[] {12.2, 108.6}, new double[] {21.0, 105.8}));

        assertThat(TrackRouteWkt.build(segments, null)).isEqualTo(
                "MULTILINESTRING((108.440000 11.940000,108.450000 11.950000),"
                        + "(108.450000 11.950000,108.600000 12.200000))");
    }

    @Test
    void aDayWithoutALineFallsBackToThePointOfItsBox() {
        assertThat(TrackRouteWkt.build(List.of(), new double[] {108.44, 11.94, 108.44, 11.94}))
                .isEqualTo("POINT(108.440000 11.940000)");
        assertThat(TrackRouteWkt.build(List.of(), null)).isNull();
    }

    private static TrackSegmentLine line(TrackSegmentKind kind, double[]... points) {
        return new TrackSegmentLine(kind, PolylineCodec.encode(List.of(points)));
    }
}
