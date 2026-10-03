package com.ds.goroute.service.track;

import com.ds.goroute.type.TrackSegmentKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds a day's statistics and simplified line from every point one person recorded that
 * day. Pure, and deliberately rebuilt from all of the day's points on every change rather
 * than patched incrementally: a deletion or a late offline upload then cannot leave stale
 * numbers behind.
 *
 * <p>Rules (.docs/designs/TRAVEL_TRACKER_PLAN.md):
 * <ul>
 *   <li>A short hop faster than {@code maxSpeedKmh} is a GPS jump and is ignored (TR-10).</li>
 *   <li>No signal for longer than {@code gapMinutes} and further than {@code gapDistanceM}
 *       becomes a dashed GAP segment; its distance still counts (TR-11).</li>
 *   <li>A hop of at least {@code flightMinKm} at {@code flightMinKmh} or more is a FLIGHT,
 *       counted in {@code flightDistanceM}, not in {@code distanceM} (TR-12).</li>
 * </ul>
 */
public final class TrackDayBuilder {

    /** The simplified line of one day never carries more points than this. */
    public static final int MAX_LINE_POINTS = 1500;

    /** Slower than this counts as standing, not moving. */
    private static final double MOVING_MIN_SPEED_MPS = 0.5;
    /** A step longer than this is not assumed to be continuous movement. */
    private static final long MOVING_MAX_STEP_SECONDS = 300;
    /** Altitude noise band: smaller changes are not counted as climbing or descending. */
    private static final int ELEVATION_HYSTERESIS_M = 3;

    private TrackDayBuilder() {
    }

    public static TrackDayResult build(List<TrackPoint> points, TrackerSettings settings) {
        List<TrackPoint> kept = removeSpikes(sortedUnique(points), settings);
        if (kept.isEmpty()) {
            return new TrackDayResult(List.of(), null, 0, 0, 0, 0, 0, 0, null, null, List.of());
        }

        List<List<TrackPoint>> trackRuns = new ArrayList<>();
        List<TrackSegmentLine> jumps = new ArrayList<>();
        List<TrackPoint> current = new ArrayList<>();
        current.add(kept.get(0));
        double distance = 0;
        double flightDistance = 0;
        long movingSeconds = 0;
        long gapMillis = settings.gapMinutes() * 60_000L;

        for (int i = 1; i < kept.size(); i++) {
            TrackPoint from = kept.get(i - 1);
            TrackPoint to = kept.get(i);
            double metres = TrackGeo.metres(from, to);
            long millis = to.timeMs() - from.timeMs();
            double seconds = millis / 1000d;
            double kmh = seconds > 0 ? metres / seconds * 3.6 : Double.MAX_VALUE;

            if (metres >= settings.flightMinKm() * 1000d && kmh >= settings.flightMinKmh()) {
                closeRun(current, trackRuns);
                jumps.add(line(TrackSegmentKind.FLIGHT, from, to));
                flightDistance += metres;
                current = new ArrayList<>(List.of(to));
            } else if (millis > gapMillis && metres > settings.gapDistanceM()) {
                closeRun(current, trackRuns);
                jumps.add(line(TrackSegmentKind.GAP, from, to));
                distance += metres;
                current = new ArrayList<>(List.of(to));
            } else {
                current.add(to);
                distance += metres;
                if (seconds > 0 && seconds <= MOVING_MAX_STEP_SECONDS
                        && metres / seconds >= MOVING_MIN_SPEED_MPS) {
                    movingSeconds += Math.round(seconds);
                }
            }
        }
        closeRun(current, trackRuns);

        List<TrackSegmentLine> segments = new ArrayList<>();
        List<double[]> allCoordinates = new ArrayList<>();
        for (List<TrackPoint> run : trackRuns) {
            List<double[]> coordinates = toLatLng(run);
            allCoordinates.addAll(coordinates);
            int budget = Math.max(2, (int) ((long) MAX_LINE_POINTS * run.size() / kept.size()));
            segments.add(new TrackSegmentLine(
                    TrackSegmentKind.TRACK,
                    PolylineCodec.encode(LineSimplifier.simplify(coordinates, budget))));
        }
        segments.addAll(jumps);
        if (allCoordinates.isEmpty()) {
            allCoordinates.addAll(toLatLng(kept));
        }

        int[] elevation = elevation(kept);
        return new TrackDayResult(
                segments,
                TrackGeo.bbox(allCoordinates),
                (int) Math.round(distance),
                (int) Math.round(flightDistance),
                (int) movingSeconds,
                elevation[0],
                elevation[1],
                kept.size(),
                kept.get(0).timeMs(),
                kept.get(kept.size() - 1).timeMs(),
                kept);
    }

    static List<TrackPoint> sortedUnique(List<TrackPoint> points) {
        List<TrackPoint> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingLong(TrackPoint::timeMs));
        List<TrackPoint> unique = new ArrayList<>(sorted.size());
        for (TrackPoint point : sorted) {
            if (unique.isEmpty() || unique.get(unique.size() - 1).timeMs() != point.timeMs()) {
                unique.add(point);
            }
        }
        return unique;
    }

    /**
     * Drops points that imply an impossible speed over a short distance. Long fast hops are
     * kept: they are flights, and the segmentation above draws them as such.
     */
    static List<TrackPoint> removeSpikes(List<TrackPoint> points, TrackerSettings settings) {
        List<TrackPoint> kept = new ArrayList<>(points.size());
        double flightMinMetres = settings.flightMinKm() * 1000d;
        for (TrackPoint point : points) {
            if (kept.isEmpty()) {
                kept.add(point);
                continue;
            }
            TrackPoint previous = kept.get(kept.size() - 1);
            double metres = TrackGeo.metres(previous, point);
            double seconds = (point.timeMs() - previous.timeMs()) / 1000d;
            double kmh = seconds > 0 ? metres / seconds * 3.6 : Double.MAX_VALUE;
            if (metres < flightMinMetres && kmh > settings.maxSpeedKmh()) {
                continue;
            }
            kept.add(point);
        }
        return kept;
    }

    /** {gain, loss} in metres with a small hysteresis so GPS altitude noise is not counted. */
    static int[] elevation(List<TrackPoint> points) {
        Integer reference = null;
        int gain = 0;
        int loss = 0;
        for (TrackPoint point : points) {
            Integer altitude = point.altitudeM();
            if (altitude == null) {
                continue;
            }
            if (reference == null) {
                reference = altitude;
                continue;
            }
            int change = altitude - reference;
            if (change >= ELEVATION_HYSTERESIS_M) {
                gain += change;
                reference = altitude;
            } else if (change <= -ELEVATION_HYSTERESIS_M) {
                loss -= change;
                reference = altitude;
            }
        }
        return new int[] {gain, loss};
    }

    private static void closeRun(List<TrackPoint> run, List<List<TrackPoint>> runs) {
        if (run.size() >= 2) {
            runs.add(run);
        }
    }

    private static TrackSegmentLine line(TrackSegmentKind kind, TrackPoint from, TrackPoint to) {
        return new TrackSegmentLine(kind, PolylineCodec.encode(List.of(
                new double[] {from.latitude(), from.longitude()},
                new double[] {to.latitude(), to.longitude()})));
    }

    private static List<double[]> toLatLng(List<TrackPoint> points) {
        List<double[]> coordinates = new ArrayList<>(points.size());
        for (TrackPoint point : points) {
            coordinates.add(new double[] {point.latitude(), point.longitude()});
        }
        return coordinates;
    }
}
