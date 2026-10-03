package com.ds.goroute.service.track;

import com.ds.goroute.type.TrackAccuracyMode;

import java.util.Map;

/**
 * The TRACKER configuration values the pure track classes need, read once per request so a
 * computation never mixes two versions of a knob.
 */
public record TrackerSettings(
        Map<TrackAccuracyMode, Integer> maxAccuracyM,
        double maxSpeedKmh,
        int gapMinutes,
        int gapDistanceM,
        int flightMinKm,
        int flightMinKmh,
        int arrivalRadiusM,
        int arrivalMinutes) {

    public int maxAccuracyFor(TrackAccuracyMode mode) {
        Integer value = maxAccuracyM.get(mode == null ? TrackAccuracyMode.BALANCED : mode);
        return value == null ? Integer.MAX_VALUE : value;
    }

    /** Values equal to the shipped defaults; convenient for tests. */
    public static TrackerSettings defaults() {
        return new TrackerSettings(
                Map.of(TrackAccuracyMode.HIGH, 30, TrackAccuracyMode.BALANCED, 60, TrackAccuracyMode.SAVER, 150),
                300, 10, 1000, 200, 300, 100, 3);
    }
}
