package com.ds.goroute.service.track;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Finds which planned activities a person actually reached, from their recorded points
 * (TR-15, server side; the phone runs the same rule live to nudge a check-in).
 *
 * <p>A visit starts at the first point within {@code radiusM} of the activity and ends at the
 * first point farther than {@code radiusM x 1.5} (hysteresis, so GPS wobble at the edge does
 * not split one stay into many). It counts when it lasted at least {@code minMinutes}.
 *
 * <p>The duration runs to the point that <em>leaves</em>, not to the last point inside: the
 * phone stops sampling while the person stands still, so a forty-minute stay can be a single
 * point inside followed by one outside forty minutes later. A point whose reported accuracy
 * is worse than the radius says nothing about being inside it and is skipped.
 */
public final class TrackVisitDetector {

    private static final double EXIT_FACTOR = 1.5;

    private TrackVisitDetector() {
    }

    /** A planned place to test against. */
    public record Target(UUID id, double latitude, double longitude) {
    }

    /**
     * One activity reached on this day. When the person came back several times, the first
     * arrival and the last departure are reported.
     *
     * @param departedMs null while the person may still be there (no point has left yet)
     */
    public record Visit(UUID targetId, long arrivedMs, Long departedMs) {
    }

    public static List<Visit> detect(List<TrackPoint> points, List<Target> targets,
                                     int radiusM, int minMinutes) {
        List<Visit> visits = new ArrayList<>();
        long minMillis = minMinutes * 60_000L;
        double exitMetres = radiusM * EXIT_FACTOR;
        for (Target target : targets) {
            Long runStart = null;
            Long firstArrival = null;
            Long lastDeparture = null;
            boolean openAtEnd = false;
            long lastInside = 0;
            for (TrackPoint point : points) {
                if (point.accuracyM() != null && point.accuracyM() > radiusM) {
                    continue;
                }
                double metres = TrackGeo.metres(
                        point.latitude(), point.longitude(), target.latitude(), target.longitude());
                if (metres <= radiusM) {
                    if (runStart == null) {
                        runStart = point.timeMs();
                    }
                    lastInside = point.timeMs();
                } else if (metres > exitMetres && runStart != null) {
                    if (point.timeMs() - runStart >= minMillis) {
                        firstArrival = firstArrival == null ? runStart : firstArrival;
                        lastDeparture = point.timeMs();
                    }
                    runStart = null;
                }
            }
            if (runStart != null && lastInside - runStart >= minMillis) {
                firstArrival = firstArrival == null ? runStart : firstArrival;
                openAtEnd = true;
            }
            if (firstArrival != null) {
                visits.add(new Visit(target.id(), firstArrival, openAtEnd ? null : lastDeparture));
            }
        }
        return visits;
    }
}
