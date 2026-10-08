package com.ds.goroute.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * The viewer-independent part of a public trip search, normalised so that two requests
 * asking for the same ranking compare equal. It is the key of the shared ranking cache and
 * the parameter object of the ranking query.
 *
 * <ul>
 *   <li>The point is rounded to two decimals (about 1 km) and the query runs on the rounded
 *       point, so a cached ranking is exactly what the query returns for that key.</li>
 *   <li>With {@code allPublic} the point and radius neither filter nor order anything, so
 *       they are dropped and every caller shares one ranking.</li>
 *   <li>{@code recommended} overrides the random seed, so the seed is dropped with it.</li>
 *   <li>Text filters are trimmed and lower-cased; the query compares them lower-cased.</li>
 * </ul>
 *
 * @param latitude     rounded search point, null when {@code allPublic}
 * @param longitude    rounded search point, null when {@code allPublic}
 * @param radiusMeters radius around the point, null when {@code allPublic}
 */
public record PublicTripSearchCriteria(
        Double latitude,
        Double longitude,
        Double radiusMeters,
        String destination,
        String keyword,
        boolean allPublic,
        String randomSeed,
        boolean preferImages,
        boolean recommended) {

    private static final int POINT_DECIMALS = 2;

    public static PublicTripSearchCriteria of(BigDecimal latitude,
                                              BigDecimal longitude,
                                              BigDecimal radiusKm,
                                              String destination,
                                              String keyword,
                                              boolean allPublic,
                                              String randomSeed,
                                              boolean preferImages,
                                              boolean recommended) {
        Double roundedLatitude = allPublic ? null : round(latitude);
        Double roundedLongitude = allPublic ? null : round(longitude);
        Double radiusMeters = null;
        if (!allPublic) {
            // A missing or negative radius matches nothing, as the old "distance <= radius" did.
            radiusMeters = radiusKm == null || radiusKm.signum() < 0
                    ? 0d
                    : radiusKm.doubleValue() * 1000d;
        }
        return new PublicTripSearchCriteria(
                roundedLatitude,
                roundedLongitude,
                radiusMeters,
                normalizeText(destination),
                normalizeText(keyword),
                allPublic,
                recommended ? null : normalizeSeed(randomSeed),
                preferImages,
                recommended);
    }

    /**
     * Whether results are ordered nearest first, the only order that needs each trip's
     * distance rather than just "within the radius".
     */
    public boolean orderedByDistance() {
        return !allPublic && !recommended && randomSeed == null;
    }

    private static Double round(BigDecimal coordinate) {
        return coordinate == null ? null : coordinate.setScale(POINT_DECIMALS, RoundingMode.HALF_UP).doubleValue();
    }

    private static String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeSeed(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
