package com.ds.goroute.service.track;

import com.ds.goroute.type.TrackSegmentKind;

import java.util.List;
import java.util.Locale;

/**
 * The geometry of a day's route as WKT (SRID 4326, x = longitude), for asking PostGIS which
 * provinces it passes through. TRACK and GAP pieces count, since a gap is still travelled on
 * the ground; a FLIGHT does not, because flying over a province is not passing through it.
 * A day without any line (a single fix) falls back to that point.
 */
public final class TrackRouteWkt {

    private TrackRouteWkt() {
    }

    /** Null when there is nothing to locate. */
    public static String build(List<TrackSegmentLine> segments, double[] bbox) {
        StringBuilder lines = new StringBuilder();
        int lineCount = 0;
        for (TrackSegmentLine segment : segments) {
            if (segment.kind() == TrackSegmentKind.FLIGHT) {
                continue;
            }
            List<double[]> coordinates = PolylineCodec.decode(segment.polyline());
            if (coordinates.size() < 2) {
                continue;
            }
            lines.append(lineCount == 0 ? "(" : ",(");
            for (int i = 0; i < coordinates.size(); i++) {
                if (i > 0) {
                    lines.append(',');
                }
                appendPoint(lines, coordinates.get(i)[1], coordinates.get(i)[0]);
            }
            lines.append(')');
            lineCount++;
        }
        if (lineCount > 0) {
            return "MULTILINESTRING(" + lines + ")";
        }
        if (bbox == null) {
            return null;
        }
        StringBuilder point = new StringBuilder("POINT(");
        appendPoint(point, (bbox[0] + bbox[2]) / 2, (bbox[1] + bbox[3]) / 2);
        return point.append(')').toString();
    }

    private static void appendPoint(StringBuilder out, double longitude, double latitude) {
        out.append(String.format(Locale.ROOT, "%.6f %.6f", longitude, latitude));
    }
}
