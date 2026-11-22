package com.ds.goroute.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeoBoundingBoxTest {

    private static final double HANOI_LAT = 21.0285;
    private static final double HANOI_LNG = 105.8542;

    @Test
    void containsAPointJustInsideTheRadiusDueNorth() {
        double radiusKm = 10;
        // 9.99 km north on the R = 6371 sphere the SQL haversine uses.
        double north = HANOI_LAT + 9.99 / GeoBoundingBox.KM_PER_DEGREE;
        assertThat(GeoDistance.kilometres(HANOI_LAT, HANOI_LNG, north, HANOI_LNG)).isLessThan(radiusKm);

        GeoBoundingBox box = GeoBoundingBox.around(HANOI_LAT, HANOI_LNG, radiusKm);

        assertThat(north).isLessThanOrEqualTo(box.maxLat());
        // The old box (111.32 km per degree) stopped short of this point.
        assertThat(north).isGreaterThan(HANOI_LAT + radiusKm / 111.32);
    }

    @ParameterizedTest
    @CsvSource({"0, 50", "90, 50", "180, 50", "270, 50", "45, 50", "315, 200", "60, 5"})
    void containsEveryPointOnTheCircle(double bearingDegrees, double radiusKm) {
        double[] edge = destination(HANOI_LAT, HANOI_LNG, bearingDegrees, radiusKm * 0.999999);
        assertThat(GeoDistance.kilometres(HANOI_LAT, HANOI_LNG, edge[0], edge[1])).isLessThanOrEqualTo(radiusKm);

        GeoBoundingBox box = GeoBoundingBox.around(HANOI_LAT, HANOI_LNG, radiusKm);

        assertThat(edge[0]).isBetween(box.minLat(), box.maxLat());
        assertThat(edge[1]).isBetween(box.minLng(), box.maxLng());
    }

    @Test
    void widestLongitudeIsPolewardOfTheCentreAndStillInside() {
        double radiusKm = 300;
        GeoBoundingBox box = GeoBoundingBox.around(HANOI_LAT, HANOI_LNG, radiusKm);
        double maxEast = HANOI_LNG;
        for (int bearing = 0; bearing <= 180; bearing++) {
            maxEast = Math.max(maxEast, destination(HANOI_LAT, HANOI_LNG, bearing, radiusKm)[1]);
        }
        assertThat(maxEast).isLessThanOrEqualTo(box.maxLng() + 1e-9);
    }

    @Test
    void zeroRadiusIsThePointItself() {
        GeoBoundingBox box = GeoBoundingBox.around(HANOI_LAT, HANOI_LNG, 0);

        assertThat(box.minLat()).isEqualTo(HANOI_LAT);
        assertThat(box.maxLng()).isEqualTo(HANOI_LNG);
    }

    /** Spherical destination point (R = 6371 km). */
    private static double[] destination(double lat, double lng, double bearingDegrees, double distanceKm) {
        double d = distanceKm / GeoBoundingBox.EARTH_RADIUS_KM;
        double phi1 = Math.toRadians(lat);
        double lambda1 = Math.toRadians(lng);
        double theta = Math.toRadians(bearingDegrees);
        double phi2 = Math.asin(Math.sin(phi1) * Math.cos(d) + Math.cos(phi1) * Math.sin(d) * Math.cos(theta));
        double lambda2 = lambda1 + Math.atan2(Math.sin(theta) * Math.sin(d) * Math.cos(phi1),
                Math.cos(d) - Math.sin(phi1) * Math.sin(phi2));
        return new double[]{Math.toDegrees(phi2), Math.toDegrees(lambda2)};
    }
}
