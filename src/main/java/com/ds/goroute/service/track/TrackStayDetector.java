package com.ds.goroute.service.track;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds where a person stopped somewhere that is not on the plan (TR-16, P2): at least
 * {@code minMinutes} within {@code radiusM} of one spot, and not within {@code excludeRadiusM}
 * of any planned activity of the same day (those are {@link TrackVisitDetector}'s job).
 *
 * <p>Same rules as {@link TrackVisitDetector}: a stay ends at the first point farther than
 * {@code radiusM x 1.5} from where it started, its duration runs to that leaving point (the
 * phone stops sampling while the person stands still), and a point whose accuracy is worse
 * than the radius is ignored. Two consecutive stays whose centres are within the radius are
 * one stay that GPS drift split in two, and are merged.
 *
 * <p>One exception to "runs to the leaving point": when the step out is a signal gap (longer
 * than {@code gapMinutes} and farther than {@code gapDistanceM}, TR-11) nobody knows when the
 * person left, so the stay ends at its last point inside.
 */
public final class TrackStayDetector {

    private static final double EXIT_FACTOR = 1.5;

    private TrackStayDetector() {
    }

    /** A planned place whose surroundings are not an off-plan stop. */
    public record Excluded(double latitude, double longitude) {
    }

    /**
     * @param departedMs null while the person may still be there (no point has left yet)
     */
    public record Stay(double latitude, double longitude, long arrivedMs, Long departedMs) {
    }

    /**
     * @param radiusM        a stay keeps within this distance of where it started
     * @param minMinutes     shortest stay reported
     * @param excludeRadiusM distance from a planned activity inside which a stay is not off-plan
     * @param gapMinutes     together with {@code gapDistanceM}: a step that is a signal gap
     */
    public record Rules(int radiusM, int minMinutes, int excludeRadiusM, int gapMinutes, int gapDistanceM) {
    }

    public static List<Stay> detect(List<TrackPoint> points, Rules rules, List<Excluded> excluded) {
        int radiusM = rules.radiusM();
        long gapMillis = rules.gapMinutes() * 60_000L;
        List<TrackPoint> usable = new ArrayList<>(points.size());
        for (TrackPoint point : points) {
            if (point.accuracyM() == null || point.accuracyM() <= radiusM) {
                usable.add(point);
            }
        }
        long minMillis = rules.minMinutes() * 60_000L;
        double exitMetres = radiusM * EXIT_FACTOR;
        List<Stay> stays = new ArrayList<>();
        int i = 0;
        while (i < usable.size()) {
            TrackPoint anchor = usable.get(i);
            int lastInside = i;
            int leave = -1;
            double sumLat = anchor.latitude();
            double sumLng = anchor.longitude();
            int inside = 1;
            for (int j = i + 1; j < usable.size(); j++) {
                TrackPoint point = usable.get(j);
                double metres = TrackGeo.metres(anchor, point);
                if (metres <= radiusM) {
                    lastInside = j;
                    sumLat += point.latitude();
                    sumLng += point.longitude();
                    inside++;
                } else if (metres > exitMetres) {
                    leave = j;
                    break;
                }
            }
            TrackPoint lastPoint = usable.get(lastInside);
            boolean isGapExit = leave >= 0
                    && usable.get(leave).timeMs() - lastPoint.timeMs() > gapMillis
                    && TrackGeo.metres(lastPoint, usable.get(leave)) > rules.gapDistanceM();
            Long departedMs = leave < 0 ? null : isGapExit ? lastPoint.timeMs() : usable.get(leave).timeMs();
            long endMs = departedMs == null ? lastPoint.timeMs() : departedMs;
            if (endMs - anchor.timeMs() >= minMillis) {
                stays.add(new Stay(sumLat / inside, sumLng / inside, anchor.timeMs(), departedMs));
                i = leave >= 0 ? leave : usable.size();
            } else {
                i++;
            }
        }
        List<Stay> merged = merge(stays, radiusM);
        List<Stay> result = new ArrayList<>(merged.size());
        for (Stay stay : merged) {
            if (!isNearAny(stay, excluded, rules.excludeRadiusM())) {
                result.add(stay);
            }
        }
        return result;
    }

    private static List<Stay> merge(List<Stay> stays, int radiusM) {
        List<Stay> merged = new ArrayList<>(stays.size());
        for (Stay stay : stays) {
            if (!merged.isEmpty()) {
                Stay previous = merged.get(merged.size() - 1);
                if (TrackGeo.metres(previous.latitude(), previous.longitude(), stay.latitude(), stay.longitude())
                        <= radiusM) {
                    merged.set(merged.size() - 1, new Stay(
                            (previous.latitude() + stay.latitude()) / 2,
                            (previous.longitude() + stay.longitude()) / 2,
                            previous.arrivedMs(),
                            stay.departedMs()));
                    continue;
                }
            }
            merged.add(stay);
        }
        return merged;
    }

    private static boolean isNearAny(Stay stay, List<Excluded> excluded, int radiusM) {
        for (Excluded place : excluded) {
            if (TrackGeo.metres(stay.latitude(), stay.longitude(), place.latitude(), place.longitude()) <= radiusM) {
                return true;
            }
        }
        return false;
    }

    /** Whether two stays are the same stop: centres within the radius and their times overlap. */
    public static boolean isSameStop(double latitudeA, double longitudeA, long arrivedA, Long departedA,
                                     double latitudeB, double longitudeB, long arrivedB, Long departedB,
                                     int radiusM) {
        if (TrackGeo.metres(latitudeA, longitudeA, latitudeB, longitudeB) > radiusM) {
            return false;
        }
        long endA = departedA == null ? Long.MAX_VALUE : departedA;
        long endB = departedB == null ? Long.MAX_VALUE : departedB;
        return arrivedA <= endB && arrivedB <= endA;
    }
}
