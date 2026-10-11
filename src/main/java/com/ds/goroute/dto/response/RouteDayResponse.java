package com.ds.goroute.dto.response;

import java.util.List;

/**
 * Road legs of one day, in the order asked.
 *
 * @param savingMode      true when some legs are straight lines because the map budget is spent
 * @param savingModeUntil when live routes come back (ISO instant), or null
 */
public record RouteDayResponse(List<Leg> legs, boolean savingMode, String savingModeUntil) {

    /**
     * @param distanceMeters road distance, or the straight-line distance when {@code source} is STRAIGHT
     * @param durationSeconds travel time; null for a straight line
     * @param polyline        encoded polyline, precision 6; null for a straight line
     * @param source          MAPBOX, GOONG, or STRAIGHT
     */
    public record Leg(int index, Integer distanceMeters, Integer durationSeconds, String polyline, String source) {
    }
}
