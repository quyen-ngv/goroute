package com.ds.goroute.service.track;

import com.ds.goroute.type.TrackSegmentKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyZoneClipperTest {

    /** Home at the middle of the line below. */
    private static final PrivacyZoneClipper.Zone HOME = new PrivacyZoneClipper.Zone(11.9450, 108.44, 200);

    @Test
    void cutsTheLineWhereItCrossesAZone() {
        List<double[]> line = new ArrayList<>();
        for (int i = 0; i <= 100; i++) {
            line.add(new double[] {11.94 + i * 0.0001, 108.44});
        }
        TrackSegmentLine segment = new TrackSegmentLine(TrackSegmentKind.TRACK, PolylineCodec.encode(line));

        PrivacyZoneClipper.Clipped clipped = PrivacyZoneClipper.clip(List.of(segment), List.of(HOME));

        assertThat(clipped.segments()).hasSize(2);
        for (TrackSegmentLine part : clipped.segments()) {
            for (double[] point : PolylineCodec.decode(part.polyline())) {
                assertThat(PrivacyZoneClipper.isHidden(point[0], point[1], List.of(HOME))).isFalse();
            }
        }
    }

    @Test
    void simplifiedStraightLineThroughAZoneIsStillCut() {
        // Both stored points are far outside home; the stretch between them runs through it.
        TrackSegmentLine straight = new TrackSegmentLine(TrackSegmentKind.TRACK, PolylineCodec.encode(List.of(
                new double[] {11.9400, 108.44}, new double[] {11.9500, 108.44})));

        PrivacyZoneClipper.Clipped clipped = PrivacyZoneClipper.clip(List.of(straight), List.of(HOME));

        assertThat(clipped.segments()).hasSize(2);
        for (TrackSegmentLine part : clipped.segments()) {
            for (double[] point : PolylineCodec.decode(part.polyline())) {
                assertThat(PrivacyZoneClipper.isHidden(point[0], point[1], List.of(HOME))).isFalse();
            }
        }
    }

    @Test
    void dropsAGapThatStartsInAZoneAndKeepsEverythingWithoutZones() {
        TrackSegmentLine gap = new TrackSegmentLine(TrackSegmentKind.GAP, PolylineCodec.encode(List.of(
                new double[] {11.9450, 108.44}, new double[] {12.2, 108.44})));

        assertThat(PrivacyZoneClipper.clip(List.of(gap), List.of(HOME)).segments()).isEmpty();
        PrivacyZoneClipper.Clipped untouched = PrivacyZoneClipper.clip(List.of(gap), List.of());
        assertThat(untouched.segments()).containsExactly(gap);
        assertThat(untouched.bbox()).hasSize(4);
    }

    @Test
    void routeEntirelyInsideAZoneLeavesNothing() {
        TrackSegmentLine inside = new TrackSegmentLine(TrackSegmentKind.TRACK, PolylineCodec.encode(List.of(
                new double[] {11.9450, 108.44}, new double[] {11.9451, 108.44}, new double[] {11.9452, 108.44})));

        PrivacyZoneClipper.Clipped clipped = PrivacyZoneClipper.clip(List.of(inside), List.of(HOME));

        assertThat(clipped.segments()).isEmpty();
        assertThat(clipped.bbox()).isNull();
    }
}
