package com.ds.goroute.service.track;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrackStayDetectorTest {

    private static final long T0 = 1_759_374_000_000L;
    private static final long MINUTE = 60_000L;
    /** 100 m radius, 10 minutes, activities within 100 m excluded, gap = 10 min and 1 km. */
    private static final TrackStayDetector.Rules RULES = new TrackStayDetector.Rules(100, 10, 100, 10, 1000);

    @Test
    void aLongStayWithOnePointInsideRunsUntilThePointThatLeaves() {
        List<TrackPoint> points = List.of(
                point(0, 11.9300, 108.4400),
                point(5, 11.9500, 108.4400),
                point(35, 11.9520, 108.4400));

        List<TrackStayDetector.Stay> stays = TrackStayDetector.detect(points, RULES, List.of());

        assertThat(stays).singleElement().satisfies(stay -> {
            assertThat(stay.arrivedMs()).isEqualTo(T0 + 5 * MINUTE);
            assertThat(stay.departedMs()).isEqualTo(T0 + 35 * MINUTE);
            assertThat(stay.latitude()).isEqualTo(11.95);
        });
    }

    @Test
    void aShortPauseIsNotAStop() {
        List<TrackPoint> points = List.of(
                point(0, 11.9500, 108.4400),
                point(6, 11.9502, 108.4400),
                point(8, 11.9600, 108.4400));

        assertThat(TrackStayDetector.detect(points, RULES, List.of())).isEmpty();
    }

    @Test
    void aStopNextToAPlannedActivityBelongsToThatActivity() {
        List<TrackPoint> points = List.of(
                point(0, 11.9500, 108.4400),
                point(30, 11.9600, 108.4400));

        assertThat(TrackStayDetector.detect(points, RULES,
                List.of(new TrackStayDetector.Excluded(11.9504, 108.4400)))).isEmpty();
    }

    @Test
    void aSignalGapAfterwardsDoesNotStretchTheStay() {
        // Last fix at the cafe at 09:05, next one 90 minutes later 4 km away: signal was lost.
        List<TrackPoint> points = List.of(
                point(0, 11.9500, 108.4400),
                point(5, 11.9501, 108.4400),
                point(95, 11.9900, 108.4400));

        assertThat(TrackStayDetector.detect(points, RULES, List.of())).isEmpty();
    }

    @Test
    void anOpenStayAtTheEndHasNoDeparture() {
        List<TrackPoint> points = List.of(
                point(0, 11.9500, 108.4400),
                point(15, 11.9501, 108.4400));

        assertThat(TrackStayDetector.detect(points, RULES, List.of()))
                .singleElement()
                .satisfies(stay -> assertThat(stay.departedMs()).isNull());
    }

    @Test
    void driftThatSplitsOneStayIsMergedAndInaccurateFixesAreIgnored() {
        // The drift to 11.9514 (155 m from where the stay began) ends a first stay and starts a
        // second one 89 m away from it; the 500 m fix says nothing.
        List<TrackPoint> points = List.of(
                point(0, 11.9500, 108.4400),
                point(12, 11.9508, 108.4400),
                new TrackPoint(T0 + 12 * MINUTE + 30_000, 11.9700, 108.4400, 500, null, null),
                point(13, 11.9514, 108.4400),
                point(30, 11.9510, 108.4400),
                point(31, 11.9700, 108.4400));

        List<TrackStayDetector.Stay> stays = TrackStayDetector.detect(points, RULES, List.of());

        assertThat(stays).singleElement().satisfies(stay -> {
            assertThat(stay.arrivedMs()).isEqualTo(T0);
            assertThat(stay.departedMs()).isEqualTo(T0 + 31 * MINUTE);
        });
    }

    @Test
    void sameStopNeedsBothPlaceAndTimeToMatch() {
        assertThat(TrackStayDetector.isSameStop(11.95, 108.44, 0, 10L, 11.9505, 108.44, 5, 20L, 100)).isTrue();
        assertThat(TrackStayDetector.isSameStop(11.95, 108.44, 0, 10L, 11.9505, 108.44, 11, 20L, 100)).isFalse();
        assertThat(TrackStayDetector.isSameStop(11.95, 108.44, 0, null, 11.96, 108.44, 5, null, 100)).isFalse();
        assertThat(TrackStayDetector.isSameStop(11.95, 108.44, 0, null, 11.9505, 108.44, 50, null, 100)).isTrue();
    }

    private static TrackPoint point(int minute, double latitude, double longitude) {
        return new TrackPoint(T0 + minute * MINUTE, latitude, longitude, 8, null, null);
    }
}
