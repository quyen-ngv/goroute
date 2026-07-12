package com.ds.goroute.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PlaceImportCandidateKey {

    private PlaceImportCandidateKey() {
    }

    public static String of(String googlePlaceId,
                            String cid,
                            BigDecimal latitude,
                            BigDecimal longitude,
                            String name,
                            String url) {
        if (!isBlank(googlePlaceId)) {
            return "place:" + googlePlaceId.trim();
        }
        if (!isBlank(cid)) {
            return "cid:" + cid.trim();
        }
        if (latitude != null && longitude != null) {
            return "coordinates:" + latitude.setScale(5, RoundingMode.HALF_UP)
                    + "," + longitude.setScale(5, RoundingMode.HALF_UP);
        }
        return "fallback:" + normalized(name) + "|" + normalized(url);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
