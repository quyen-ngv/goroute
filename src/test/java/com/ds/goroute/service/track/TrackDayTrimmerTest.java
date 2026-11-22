package com.ds.goroute.service.track;

import com.ds.goroute.type.TrackSegmentKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TrackDayTrimmerTest {

    /** 0.001 degree of latitude is about 111 m. */
    private static final double STEP = 0.001;

    @Test
    void cutsThreeHundredMetresFromBothEndsAlongTheLine() {
        // 10 steps north, about 1.1 km.
        TrackSegmentLine line = track(northLine(11.94, 108.44, 10));

        List<TrackSegmentLine> trimmed = TrackDayTrimmer.trim(List.of(line), 300);

        assertThat(trimmed).singleElement().satisfies(segment -> {
            List<double[]> points = PolylineCodec.decode(segment.polyline());
            double[] first = points.get(0);
            double[] last = points.get(points.size() - 1);
            assertThat(TrackGeo.metres(11.94, 108.44, first[0], first[1])).isCloseTo(300, within(3.0));
            assertThat(TrackGeo.metres(11.95, 108.44, last[0], last[1])).isCloseTo(300, within(3.0));
        });
    }

    @Test
    void theTrimRunsAcrossPiecesAndDropsAPieceShorterThanIt() {
        // First piece about 111 m, second about 555 m.
        TrackSegmentLine shortFirst = track(northLine(11.94, 108.44, 1));
        TrackSegmentLine longSecond = track(northLine(11.942, 108.44, 5));

        List<TrackSegmentLine> trimmed = TrackDayTrimmer.trim(List.of(shortFirst, longSecond), 150);

        assertThat(trimmed).singleElement().satisfies(segment -> {
            double[] first = PolylineCodec.decode(segment.polyline()).get(0);
            // 150 - 111 = 39 m into the second piece.
            assertThat(TrackGeo.metres(11.942, 108.44, first[0], first[1])).isCloseTo(39, within(3.0));
        });
    }

    @Test
    void aDayShorterThanBothEndsTogetherDisappears() {
        assertThat(TrackDayTrimmer.trim(List.of(track(northLine(11.94, 108.44, 5))), 300)).isEmpty();
    }

    @Test
    void jumpsTouchingTheDayEndsAreDroppedAndOthersKept() {
        TrackSegmentLine morning = track(northLine(11.94, 108.44, 10));
        TrackSegmentLine gapFromStart = new TrackSegmentLine(TrackSegmentKind.GAP,
                PolylineCodec.encode(List.of(new double[] {11.9401, 108.44}, new double[] {12.2, 108.6})));
        TrackSegmentLine flightFarAway = new TrackSegmentLine(TrackSegmentKind.FLIGHT,
                PolylineCodec.encode(List.of(new double[] {12.2, 108.6}, new double[] {16.0, 108.2})));

        List<TrackSegmentLine> trimmed = TrackDayTrimmer.trim(List.of(morning, gapFromStart, flightFarAway), 300);

        assertThat(trimmed).extracting(TrackSegmentLine::kind)
                .containsExactly(TrackSegmentKind.TRACK, TrackSegmentKind.FLIGHT);
    }

    @Test
    void aDayWithoutAnyTrackShowsNothing() {
        TrackSegmentLine flight = new TrackSegmentLine(TrackSegmentKind.FLIGHT,
                PolylineCodec.encode(List.of(new double[] {10.8, 106.6}, new double[] {21.0, 105.8})));

        assertThat(TrackDayTrimmer.trim(List.of(flight), 300)).isEmpty();
    }

    private static List<double[]> northLine(double latitude, double longitude, int steps) {
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i <= steps; i++) {
            points.add(new double[] {latitude + i * STEP, longitude});
        }
        return points;
    }

    private static TrackSegmentLine track(List<double[]> points) {
        return new TrackSegmentLine(TrackSegmentKind.TRACK, PolylineCodec.encode(points));
    }
}
