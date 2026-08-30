package com.ds.goroute.utils;

import java.math.BigDecimal;

/**
 * Great-circle distance between two coordinates.
 *
 * <p>Extracted because several features need the same answer -- review location
 * verification, activity check-in radius, free check-in verification and place search
 * ranking -- and every copy of the haversine formula is another place for the radius to
 * drift. {@code GeoDistanceUtils} was exactly that second copy and now lives here.
 *
 * <p>Callers differ only in the unit they want and in what an unknown point should mean,
 * so those are the two things the entry points vary.
 */
public final class GeoDistance {

    private static final double EARTH_RADIUS_KM = 6371.0d;
    private static final double EARTH_RADIUS_METERS = EARTH_RADIUS_KM * 1000d;

    private GeoDistance() {
    }

    /** Returns {@code null} when either point is unknown, rather than a misleading zero. */
    public static Double betweenOrNull(BigDecimal fromLatitude, BigDecimal fromLongitude,
                                       BigDecimal toLatitude, BigDecimal toLongitude) {
        if (isIncomplete(fromLatitude, fromLongitude, toLatitude, toLongitude)) {
            return null;
        }
        return between(fromLatitude.doubleValue(), fromLongitude.doubleValue(),
                toLatitude.doubleValue(), toLongitude.doubleValue());
    }

    /** Metres between two known points. */
    public static double between(double fromLatitude, double fromLongitude,
                                 double toLatitude, double toLongitude) {
        return haversine(EARTH_RADIUS_METERS, fromLatitude, fromLongitude, toLatitude, toLongitude);
    }

    /**
     * Kilometres, with an unknown point reported as {@link Double#MAX_VALUE} so a
     * distance sort pushes it to the end instead of the front.
     */
    public static double kilometresOrFarAway(BigDecimal fromLatitude, BigDecimal fromLongitude,
                                             BigDecimal toLatitude, BigDecimal toLongitude) {
        if (isIncomplete(fromLatitude, fromLongitude, toLatitude, toLongitude)) {
            return Double.MAX_VALUE;
        }
        return kilometres(fromLatitude.doubleValue(), fromLongitude.doubleValue(),
                toLatitude.doubleValue(), toLongitude.doubleValue());
    }

    /** Kilometres between two known points. */
    public static double kilometres(double fromLatitude, double fromLongitude,
                                    double toLatitude, double toLongitude) {
        return haversine(EARTH_RADIUS_KM, fromLatitude, fromLongitude, toLatitude, toLongitude);
    }

    private static boolean isIncomplete(BigDecimal fromLatitude, BigDecimal fromLongitude,
                                        BigDecimal toLatitude, BigDecimal toLongitude) {
        return fromLatitude == null || fromLongitude == null
                || toLatitude == null || toLongitude == null;
    }

    private static double haversine(double earthRadius,
                                    double fromLatitude, double fromLongitude,
                                    double toLatitude, double toLongitude) {
        double deltaLatitude = Math.toRadians(toLatitude - fromLatitude);
        double deltaLongitude = Math.toRadians(toLongitude - fromLongitude);
        double a = Math.sin(deltaLatitude / 2) * Math.sin(deltaLatitude / 2)
                + Math.cos(Math.toRadians(fromLatitude)) * Math.cos(Math.toRadians(toLatitude))
                * Math.sin(deltaLongitude / 2) * Math.sin(deltaLongitude / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
