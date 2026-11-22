package com.ds.goroute.service.track;

import com.ds.goroute.type.TrackSegmentKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrackRouteCoarsenerTest {

    @Test
    void walkedLinesShareThePointBudgetAndJumpsStayAsTheyAre() {
        TrackSegmentLine morning = walked(11.94, 600);
        TrackSegmentLine afternoon = walked(11.98, 200);
        TrackSegmentLine gap = new TrackSegmentLine(TrackSegmentKind.GAP,
                PolylineCodec.encode(List.of(new double[] {11.95, 108.44}, new double[] {11.98, 108.44})));

        TrackRouteCoarsener.Coarsened result = TrackRouteCoarsener.coarsen(List.of(morning, gap, afternoon), 100);

        int walkedPoints = 0;
        for (TrackSegmentLine segment : result.segments()) {
            if (segment.kind() == TrackSegmentKind.TRACK) {
                walkedPoints += PolylineCodec.decode(segment.polyline()).size();
            }
        }
        assertThat(walkedPoints).isLessThanOrEqualTo(100);
        assertThat(result.segments()).extracting(TrackSegmentLine::kind)
                .containsExactly(TrackSegmentKind.TRACK, TrackSegmentKind.TRACK, TrackSegmentKind.GAP);
        assertThat(result.segments().get(2)).isEqualTo(gap);
        assertThat(result.bbox()[1]).isEqualTo(11.94);
        assertThat(result.bbox()[3]).isGreaterThan(11.98);
    }

    @Test
    void nothingDrawableGivesNoSegmentsAndNoBox() {
        TrackRouteCoarsener.Coarsened result = TrackRouteCoarsener.coarsen(List.of(), 100);

        assertThat(result.segments()).isEmpty();
        assertThat(result.bbox()).isNull();
    }

    /** A zig-zag north from {@code startLat}, so simplification has corners to keep. */
    private static TrackSegmentLine walked(double startLat, int count) {
        List<double[]> line = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            line.add(new double[] {startLat + i * 0.0001, 108.44 + (i % 2) * 0.0005});
        }
        return new TrackSegmentLine(TrackSegmentKind.TRACK, PolylineCodec.encode(line));
    }
}
