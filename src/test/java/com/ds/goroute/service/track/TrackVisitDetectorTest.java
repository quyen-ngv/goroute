package com.ds.goroute.service.track;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrackVisitDetectorTest {

    private static final long T0 = 1_759_374_000_000L;
    private static final long MINUTE = 60_000L;
    private static final UUID MARKET = UUID.randomUUID();
    private static final List<TrackVisitDetector.Target> TARGETS =
            List.of(new TrackVisitDetector.Target(MARKET, 11.9429, 108.4372));

    @Test
    void longStayWithOnePointInsideCountsUntilThePointThatLeaves() {
        // Recording pauses while standing still: one point inside, the next one 40 minutes later outside.
        List<TrackPoint> points = List.of(
                point(0, 11.9500, 108.4372),
                point(5, 11.9430, 108.4372),
                point(45, 11.9500, 108.4372));

        List<TrackVisitDetector.Visit> visits = TrackVisitDetector.detect(points, TARGETS, 100, 3);

        assertThat(visits).containsExactly(new TrackVisitDetector.Visit(MARKET, T0 + 5 * MINUTE, T0 + 45 * MINUTE));
    }

    @Test
    void drivingPastIsNotAVisit() {
        List<TrackPoint> points = List.of(
                point(0, 11.9500, 108.4372),
                point(1, 11.9430, 108.4372),
                point(2, 11.9350, 108.4372));

        assertThat(TrackVisitDetector.detect(points, TARGETS, 100, 3)).isEmpty();
    }

    @Test
    void wobbleAtTheEdgeDoesNotSplitTheStay() {
        // ~120 m away is outside the 100 m radius but inside the 150 m exit band.
        List<TrackPoint> points = List.of(
                point(0, 11.9430, 108.4372),
                point(2, 11.9440, 108.4372),
                point(4, 11.9430, 108.4372),
                point(10, 11.9500, 108.4372));

        List<TrackVisitDetector.Visit> visits = TrackVisitDetector.detect(points, TARGETS, 100, 3);

        assertThat(visits).containsExactly(new TrackVisitDetector.Visit(MARKET, T0, T0 + 10 * MINUTE));
    }

    @Test
    void stillThereAtTheEndHasNoDeparture() {
        List<TrackPoint> points = List.of(point(0, 11.9430, 108.4372), point(4, 11.9431, 108.4372));

        assertThat(TrackVisitDetector.detect(points, TARGETS, 100, 3))
                .containsExactly(new TrackVisitDetector.Visit(MARKET, T0, null));
    }

    @Test
    void impreciseFixesSayNothingAboutBeingInside() {
        List<TrackPoint> points = List.of(
                new TrackPoint(T0, 11.9430, 108.4372, 500, null, null),
                new TrackPoint(T0 + 10 * MINUTE, 11.9430, 108.4372, 500, null, null));

        assertThat(TrackVisitDetector.detect(points, TARGETS, 100, 3)).isEmpty();
    }

    private static TrackPoint point(int minute, double lat, double lng) {
        return new TrackPoint(T0 + minute * MINUTE, lat, lng, 10, null, null);
    }
}
