package com.ds.goroute.utils;

import java.math.BigDecimal;

/**
 * Great-circle distance between two coordinates, in metres.
 *
 * <p>Extracted because three features now need the same answer -- review location
 * verification, activity check-in radius and free check-in verification -- and three
 * copies of the haversine formula is three places for the radius to drift.
 */
public final class GeoDistance {

    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    private GeoDistance() {
    }

    /** Returns {@code null} when either point is unknown, rather than a misleading zero. */
    public static Double betweenOrNull(BigDecimal fromLatitude, BigDecimal fromLongitude,
                                       BigDecimal toLatitude, BigDecimal toLongitude) {
        if (fromLatitude == null || fromLongitude == null || toLatitude == null || toLongitude == null) {
            return null;
        }
        return between(fromLatitude.doubleValue(), fromLongitude.doubleValue(),
                toLatitude.doubleValue(), toLongitude.doubleValue());
    }

    public static double between(double fromLatitude, double fromLongitude,
                                 double toLatitude, double toLongitude) {
        double deltaLatitude = Math.toRadians(toLatitude - fromLatitude);
        double deltaLongitude = Math.toRadians(toLongitude - fromLongitude);
        double a = Math.sin(deltaLatitude / 2) * Math.sin(deltaLatitude / 2)
                + Math.cos(Math.toRadians(fromLatitude)) * Math.cos(Math.toRadians(toLatitude))
                * Math.sin(deltaLongitude / 2) * Math.sin(deltaLongitude / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
