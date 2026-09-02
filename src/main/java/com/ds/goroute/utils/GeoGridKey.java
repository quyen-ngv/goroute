package com.ds.goroute.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Snaps a coordinate to a coarse grid so nearby points share one cached upstream call.
 *
 * <p>Two decimals is roughly a 1.1 km cell, far finer than any weather model resolution
 * available over Vietnam, so collapsing a whole city block onto one key costs no accuracy
 * while keeping the free Open-Meteo tier comfortably within its daily call budget.
 */
public final class GeoGridKey {

    private static final int GRID_SCALE = 2;

    private GeoGridKey() {
    }

    public static String of(BigDecimal latitude, BigDecimal longitude) {
        return snap(latitude) + "," + snap(longitude);
    }

    private static String snap(BigDecimal coordinate) {
        if (coordinate == null) {
            return "na";
        }
        return coordinate.setScale(GRID_SCALE, RoundingMode.HALF_UP).toPlainString();
    }
}
