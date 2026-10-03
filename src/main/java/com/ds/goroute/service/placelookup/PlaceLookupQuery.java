package com.ds.goroute.service.placelookup;

import java.math.BigDecimal;

/**
 * A free-text place search, optionally biased towards a point.
 *
 * @param text             the name or phrase to search for, sent as given
 * @param biasLatitude     centre of the bias area; set together with {@code biasLongitude}
 * @param biasLongitude    centre of the bias area; set together with {@code biasLatitude}
 * @param biasRadiusMeters radius of the bias area, or null for the provider's own default
 * @param limit            maximum number of hits wanted
 */
public record PlaceLookupQuery(
        String text,
        BigDecimal biasLatitude,
        BigDecimal biasLongitude,
        Integer biasRadiusMeters,
        int limit) {

    public PlaceLookupQuery {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text is required");
        }
        if ((biasLatitude == null) != (biasLongitude == null)) {
            throw new IllegalArgumentException("biasLatitude and biasLongitude must be set together");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }

    public static PlaceLookupQuery text(String text, int limit) {
        return new PlaceLookupQuery(text, null, null, null, limit);
    }

    public boolean hasBias() {
        return biasLatitude != null;
    }
}
