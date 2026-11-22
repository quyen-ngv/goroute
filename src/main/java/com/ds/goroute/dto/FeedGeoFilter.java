package com.ds.goroute.dto;

import java.math.BigDecimal;

/**
 * The Feed's "near you" tab: only posts within {@code radiusKm} of a point.
 *
 * <p>A post is placed where its catalogue place is, falling back to the coordinates the
 * post itself carries: a check-in's own coordinates can be the phone's position when the
 * photo came from the gallery at home.
 */
public record FeedGeoFilter(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm) {

    /**
     * The app's map zoomed out to several cities asks for the check-ins of the area in view, up
     * to the length of Vietnam; that far the filter keeps out little and the query reads most
     * of the table, as the unscoped feed does.
     */
    public static final BigDecimal MAX_RADIUS_KM = BigDecimal.valueOf(1000);

    /**
     * Null when the request named no point, which keeps the feed unscoped. A point with only
     * one coordinate is treated the same way rather than guessed at.
     */
    public static FeedGeoFilter of(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm) {
        if (latitude == null || longitude == null || radiusKm == null || radiusKm.signum() <= 0) {
            return null;
        }
        return new FeedGeoFilter(latitude, longitude, radiusKm.min(MAX_RADIUS_KM));
    }
}
