package com.ds.goroute.service.track;

import com.ds.goroute.type.TrackSegmentKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TrackDayBuilderTest {

    private static final long T0 = 1_759_374_000_000L;
    /** About 11.1 m of latitude. */
    private static final double STEP_DEG = 0.0001;

    private final TrackerSettings settings = TrackerSettings.defaults();

    @Test
    void walkingLineGivesDistanceMovingTimeAndOneTrackSegment() {
        // 100 points, 11.1 m apart, 10 s apart: a 1.1 km walk at 4 km/h.
        List<TrackPoint> points = walk(T0, 11.94, 108.44, 100, 10_000);

        TrackDayResult result = TrackDayBuilder.build(points, settings);

        assertThat(result.pointCount()).isEqualTo(100);
        assertThat((double) result.distanceM()).isCloseTo(1100, within(15d));
        assertThat(result.movingSeconds()).isEqualTo(990);
        assertThat(result.segments()).extracting(TrackSegmentLine::kind).containsExactly(TrackSegmentKind.TRACK);
        assertThat(result.firstAtMs()).isEqualTo(T0);
        assertThat(result.lastAtMs()).isEqualTo(T0 + 99 * 10_000L);
        assertThat(result.bbox()).hasSize(4);
    }

    @Test
    void gpsJumpIsIgnored() {
        List<TrackPoint> points = new ArrayList<>(walk(T0, 11.94, 108.44, 5, 10_000));
        // 5 km away two seconds later: impossible on the ground, and not long enough to be a flight.
        points.add(new TrackPoint(T0 + 42_000, 11.985, 108.44, 5, null, null));
        points.addAll(walk(T0 + 50_000, 11.9405, 108.44, 5, 10_000));

        TrackDayResult result = TrackDayBuilder.build(points, settings);

        assertThat(result.pointCount()).isEqualTo(10);
        assertThat((double) result.distanceM()).isLessThan(200);
    }

    @Test
    void longSilenceFarAwayBecomesAGapThatStillCounts() {
        List<TrackPoint> points = new ArrayList<>(walk(T0, 11.94, 108.44, 5, 10_000));
        // 30 minutes without signal, then 5 km further on (a bus through a valley).
        points.addAll(walk(T0 + 40_000 + 30 * 60_000, 11.985, 108.44, 5, 10_000));

        TrackDayResult result = TrackDayBuilder.build(points, settings);

        assertThat(result.segments()).extracting(TrackSegmentLine::kind)
                .containsExactlyInAnyOrder(TrackSegmentKind.TRACK, TrackSegmentKind.TRACK, TrackSegmentKind.GAP);
        assertThat((double) result.distanceM()).isGreaterThan(5_000);
        assertThat(result.flightDistanceM()).isZero();
    }

    @Test
    void longFastHopIsAFlightCountedApart() {
        List<TrackPoint> points = new ArrayList<>(walk(T0, 10.8, 106.65, 5, 10_000));
        // Ho Chi Minh City to Hanoi (~1,150 km) in two hours.
        points.addAll(walk(T0 + 40_000 + 2 * 3_600_000, 21.02, 105.85, 5, 10_000));

        TrackDayResult result = TrackDayBuilder.build(points, settings);

        assertThat(result.segments()).extracting(TrackSegmentLine::kind).contains(TrackSegmentKind.FLIGHT);
        assertThat(result.flightDistanceM()).isGreaterThan(1_000_000);
        assertThat((double) result.distanceM()).isLessThan(200);
    }

    @Test
    void elevationIgnoresNoiseAndCountsRealClimbs() {
        List<TrackPoint> points = new ArrayList<>();
        int[] altitudes = {1500, 1501, 1499, 1500, 1510, 1520, 1530, 1528, 1515};
        for (int i = 0; i < altitudes.length; i++) {
            points.add(new TrackPoint(T0 + i * 10_000L, 11.94 + i * STEP_DEG, 108.44, 5, altitudes[i], null));
        }

        TrackDayResult result = TrackDayBuilder.build(points, settings);

        assertThat(result.elevationGainM()).isEqualTo(30);
        assertThat(result.elevationLossM()).isEqualTo(15);
    }

    @Test
    void duplicateTimestampsFromResentChunksCountOnce() {
        List<TrackPoint> points = new ArrayList<>(walk(T0, 11.94, 108.44, 10, 10_000));
        points.addAll(walk(T0, 11.94, 108.44, 10, 10_000));

        assertThat(TrackDayBuilder.build(points, settings).pointCount()).isEqualTo(10);
    }

    @Test
    void noPointsIsEmpty() {
        TrackDayResult result = TrackDayBuilder.build(List.of(), settings);

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.segments()).isEmpty();
    }

    static List<TrackPoint> walk(long start, double lat, double lng, int count, long stepMs) {
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            points.add(new TrackPoint(start + i * stepMs, lat + i * STEP_DEG, lng, 5, null, null));
        }
        return points;
    }
}
