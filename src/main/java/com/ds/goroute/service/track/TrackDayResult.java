package com.ds.goroute.service.track;

import java.util.List;

/**
 * Everything derived from one person's points on one local day.
 *
 * @param keptPoints the points that survived spike removal, in time order; visit detection
 *                   runs on these so a GPS jump cannot fake an arrival
 */
public record TrackDayResult(
        List<TrackSegmentLine> segments,
        double[] bbox,
        int distanceM,
        int flightDistanceM,
        int movingSeconds,
        int elevationGainM,
        int elevationLossM,
        int pointCount,
        Long firstAtMs,
        Long lastAtMs,
        List<TrackPoint> keptPoints) {

    public boolean isEmpty() {
        return pointCount == 0;
    }
}
