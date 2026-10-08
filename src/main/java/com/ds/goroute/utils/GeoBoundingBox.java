package com.ds.goroute.utils;

/**
 * Latitude/longitude box that contains every point within a great-circle radius of a centre, used as an
 * index-friendly prefilter in front of a haversine ({@code 6371 * acos(...)}) radius check.
 *
 * <p>The box uses the same sphere as the haversine (R = 6371 km, so one degree of latitude is
 * {@value #EARTH_RADIUS_KM}·π/180 ≈ 111.195 km). A box built with a larger km-per-degree (the old 111.32)
 * is smaller than the circle and silently drops points near its edge. The longitude half-width is the exact
 * extent of a spherical cap, {@code asin(sin(d) / cos(lat))}, which is wider than {@code d / cos(lat)}:
 * the circle reaches furthest east/west slightly poleward of the centre latitude.
 */
public record GeoBoundingBox(double minLat, double maxLat, double minLng, double maxLng) {

    public static final double EARTH_RADIUS_KM = 6371.0d;
    public static final double KM_PER_DEGREE = EARTH_RADIUS_KM * Math.PI / 180.0d;

    /** Box around the centre that contains the whole circle of {@code radiusKm}. */
    public static GeoBoundingBox around(double latitude, double longitude, double radiusKm) {
        double radius = Math.max(0d, radiusKm);
        double latDelta = radius / KM_PER_DEGREE;
        double angularRadius = radius / EARTH_RADIUS_KM;
        double lngDelta;
        double cosLat = Math.cos(Math.toRadians(latitude));
        double ratio = cosLat <= 0d ? Double.POSITIVE_INFINITY : Math.sin(Math.min(angularRadius, Math.PI / 2)) / cosLat;
        if (angularRadius >= Math.PI / 2 || Math.abs(latitude) + latDelta >= 90d || ratio >= 1d) {
            // The circle reaches a pole or wraps half the globe: every longitude can be inside.
            lngDelta = 180d;
        } else {
            lngDelta = Math.toDegrees(Math.asin(ratio));
        }
        return new GeoBoundingBox(latitude - latDelta, latitude + latDelta,
                longitude - lngDelta, longitude + lngDelta);
    }
}
