package com.ds.goroute.service.track;

import com.ds.goroute.type.TrackSegmentKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes the parts of a route that fall inside the owner's privacy zones before anyone else
 * sees it (TR-23), including other members of the same trip. Clipping happens when the route
 * is read, never when it is stored, so editing a zone takes effect immediately and can be
 * undone.
 */
public final class PrivacyZoneClipper {

    /**
     * Stored lines are simplified, so a straight stretch past someone's home can be two points
     * that both lie outside the zone. Each edge is therefore re-sampled at this spacing before
     * cutting; the smallest zone is 200 m, so no edge can cross one unseen.
     */
    static final double DENSIFY_STEP_M = 25;

    private PrivacyZoneClipper() {
    }

    /** A circle to hide. */
    public record Zone(double latitude, double longitude, int radiusM) {
    }

    /** Clipped segments and the bounding box of what is left (null when nothing is). */
    public record Clipped(List<TrackSegmentLine> segments, double[] bbox) {
    }

    public static Clipped clip(List<TrackSegmentLine> segments, List<Zone> zones) {
        if (zones.isEmpty()) {
            List<double[]> all = new ArrayList<>();
            for (TrackSegmentLine segment : segments) {
                all.addAll(PolylineCodec.decode(segment.polyline()));
            }
            return new Clipped(segments, TrackGeo.bbox(all));
        }
        List<TrackSegmentLine> result = new ArrayList<>();
        List<double[]> visible = new ArrayList<>();
        for (TrackSegmentLine segment : segments) {
            List<double[]> coordinates = PolylineCodec.decode(segment.polyline());
            if (segment.kind() != TrackSegmentKind.TRACK) {
                // A gap or flight is two points; showing half of one would be meaningless.
                if (coordinates.stream().noneMatch(point -> isHidden(point, zones))) {
                    result.add(segment);
                    visible.addAll(coordinates);
                }
                continue;
            }
            List<double[]> run = new ArrayList<>();
            for (double[] point : densify(coordinates)) {
                if (isHidden(point, zones)) {
                    flush(run, result, visible);
                    run = new ArrayList<>();
                } else {
                    run.add(point);
                }
            }
            flush(run, result, visible);
        }
        return new Clipped(result, TrackGeo.bbox(visible));
    }

    public static boolean isHidden(double latitude, double longitude, List<Zone> zones) {
        return isHidden(new double[] {latitude, longitude}, zones);
    }

    private static boolean isHidden(double[] point, List<Zone> zones) {
        for (Zone zone : zones) {
            if (TrackGeo.metres(point[0], point[1], zone.latitude(), zone.longitude()) <= zone.radiusM()) {
                return true;
            }
        }
        return false;
    }

    /** Adds evenly spaced points along every edge longer than {@link #DENSIFY_STEP_M}. */
    static List<double[]> densify(List<double[]> line) {
        if (line.size() < 2) {
            return line;
        }
        List<double[]> dense = new ArrayList<>();
        dense.add(line.get(0));
        for (int i = 1; i < line.size(); i++) {
            double[] from = line.get(i - 1);
            double[] to = line.get(i);
            int steps = (int) Math.ceil(TrackGeo.metres(from[0], from[1], to[0], to[1]) / DENSIFY_STEP_M);
            for (int step = 1; step < steps; step++) {
                double t = step / (double) steps;
                dense.add(new double[] {from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t});
            }
            dense.add(to);
        }
        return dense;
    }

    private static void flush(List<double[]> run, List<TrackSegmentLine> result, List<double[]> visible) {
        if (run.size() >= 2) {
            // Undo the densifying: the simplified path stays within a few metres of the cut one.
            result.add(new TrackSegmentLine(TrackSegmentKind.TRACK,
                    PolylineCodec.encode(LineSimplifier.simplify(run, TrackDayBuilder.MAX_LINE_POINTS))));
            visible.addAll(run);
        }
    }
}
