package com.ds.goroute.service.track;

import com.ds.goroute.type.TrackSegmentKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hides where a day started and ended from people outside the trip (TR-23, "hide the first
 * and last 300 m"): the first and last {@code trimM} metres along the recorded line are cut
 * off. Applied only to what the public sees, when the owner kept {@code trimDayEnds} on.
 *
 * <p>The TRACK pieces of a stored day are in time order, so they are walked as one chain from
 * both ends. A GAP or FLIGHT piece is two points and cannot be shortened meaningfully; it is
 * dropped when either end lies within {@code trimM} of where the day started or ended.
 */
public final class TrackDayTrimmer {

    private TrackDayTrimmer() {
    }

    public static List<TrackSegmentLine> trim(List<TrackSegmentLine> segments, double trimM) {
        List<List<double[]>> runs = new ArrayList<>();
        List<TrackSegmentLine> jumps = new ArrayList<>();
        for (TrackSegmentLine segment : segments) {
            if (segment.kind() == TrackSegmentKind.TRACK) {
                List<double[]> coordinates = PolylineCodec.decode(segment.polyline());
                if (!coordinates.isEmpty()) {
                    runs.add(coordinates);
                }
            } else {
                jumps.add(segment);
            }
        }
        if (runs.isEmpty()) {
            // Without a track the jumps' ends are exactly where the day started and ended.
            return List.of();
        }
        double[] dayStart = runs.get(0).get(0);
        List<double[]> lastRun = runs.get(runs.size() - 1);
        double[] dayEnd = lastRun.get(lastRun.size() - 1);

        cutFront(runs, trimM);
        for (List<double[]> run : runs) {
            Collections.reverse(run);
        }
        Collections.reverse(runs);
        cutFront(runs, trimM);
        Collections.reverse(runs);
        for (List<double[]> run : runs) {
            Collections.reverse(run);
        }

        List<TrackSegmentLine> result = new ArrayList<>();
        for (List<double[]> run : runs) {
            if (run.size() >= 2) {
                result.add(new TrackSegmentLine(TrackSegmentKind.TRACK, PolylineCodec.encode(run)));
            }
        }
        for (TrackSegmentLine jump : jumps) {
            boolean nearEnds = false;
            for (double[] point : PolylineCodec.decode(jump.polyline())) {
                if (TrackGeo.metres(point[0], point[1], dayStart[0], dayStart[1]) <= trimM
                        || TrackGeo.metres(point[0], point[1], dayEnd[0], dayEnd[1]) <= trimM) {
                    nearEnds = true;
                    break;
                }
            }
            if (!nearEnds) {
                result.add(jump);
            }
        }
        return result;
    }

    /** Removes {@code metres} from the start of the chain of runs, in place. */
    private static void cutFront(List<List<double[]>> runs, double metres) {
        double remaining = metres;
        while (!runs.isEmpty() && remaining > 0) {
            List<double[]> run = runs.get(0);
            List<double[]> kept = cutRunFront(run, remaining);
            if (kept == null) {
                remaining -= length(run);
                runs.remove(0);
            } else {
                runs.set(0, kept);
                remaining = 0;
            }
        }
    }

    /** The run without its first {@code metres}, or null when the run is not longer than that. */
    private static List<double[]> cutRunFront(List<double[]> run, double metres) {
        double walked = 0;
        for (int i = 1; i < run.size(); i++) {
            double[] from = run.get(i - 1);
            double[] to = run.get(i);
            double step = TrackGeo.metres(from[0], from[1], to[0], to[1]);
            if (walked + step > metres) {
                double t = step == 0 ? 0 : (metres - walked) / step;
                List<double[]> kept = new ArrayList<>(run.size() - i + 1);
                kept.add(new double[] {from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t});
                kept.addAll(run.subList(i, run.size()));
                return kept;
            }
            walked += step;
        }
        return null;
    }

    private static double length(List<double[]> run) {
        double total = 0;
        for (int i = 1; i < run.size(); i++) {
            total += TrackGeo.metres(run.get(i - 1)[0], run.get(i - 1)[1], run.get(i)[0], run.get(i)[1]);
        }
        return total;
    }
}
