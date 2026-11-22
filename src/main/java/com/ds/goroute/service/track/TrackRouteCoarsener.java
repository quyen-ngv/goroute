package com.ds.goroute.service.track;

import com.ds.goroute.type.TrackSegmentKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Thins a whole trip's route so many trips fit on one overview map ("Hành trình của tôi").
 * Pure. Walked lines share a point budget in proportion to their length in points; gap and
 * flight segments are two points each and are kept as they are.
 */
public final class TrackRouteCoarsener {

    private TrackRouteCoarsener() {
    }

    /** The thinned segments and the box around them, {@code [minLng, minLat, maxLng, maxLat]} or null. */
    public record Coarsened(List<TrackSegmentLine> segments, double[] bbox) {
    }

    public static Coarsened coarsen(List<TrackSegmentLine> segments, int maxPoints) {
        List<List<double[]>> walked = new ArrayList<>();
        List<TrackSegmentLine> jumps = new ArrayList<>();
        int walkedPoints = 0;
        for (TrackSegmentLine segment : segments) {
            List<double[]> line;
            try {
                line = PolylineCodec.decode(segment.polyline());
            } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
                continue;
            }
            if (line.size() < 2) {
                continue;
            }
            if (segment.kind() == TrackSegmentKind.TRACK) {
                walked.add(line);
                walkedPoints += line.size();
            } else {
                jumps.add(segment);
            }
        }

        List<TrackSegmentLine> result = new ArrayList<>();
        List<double[]> allCoordinates = new ArrayList<>();
        for (List<double[]> line : walked) {
            int budget = Math.max(2, (int) ((long) maxPoints * line.size() / walkedPoints));
            List<double[]> simplified = LineSimplifier.simplify(line, budget);
            allCoordinates.addAll(simplified);
            result.add(new TrackSegmentLine(TrackSegmentKind.TRACK, PolylineCodec.encode(simplified)));
        }
        for (TrackSegmentLine jump : jumps) {
            allCoordinates.addAll(PolylineCodec.decode(jump.polyline()));
            result.add(jump);
        }
        return new Coarsened(result, TrackGeo.bbox(allCoordinates));
    }
}
