package com.ds.goroute.type;

import java.util.Locale;

/** Which side of the marketplace a booking row lives on: a hotel stay or an activity order. */
public enum MarketplaceBookingType {
    HOTEL,
    ACTIVITY;

    /** Parses a caller-supplied value against this closed set; returns {@code null} when unknown. */
    public static MarketplaceBookingType parse(String value) {
        if (value == null || value.isBlank()) return null;
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return null; }
    }
}
