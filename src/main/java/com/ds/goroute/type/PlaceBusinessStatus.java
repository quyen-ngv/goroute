package com.ds.goroute.type;

import java.util.Locale;

/**
 * Whether a place is trading, read from the free-text {@code places.status} the scraper copies
 * from Google ("Permanently closed", "Temporarily closed", empty while open).
 */
public enum PlaceBusinessStatus {
    OPEN,
    TEMPORARILY_CLOSED,
    PERMANENTLY_CLOSED;

    /** The text stored in {@code places.status} for a closure. */
    public static final String PERMANENTLY_CLOSED_TEXT = "Permanently closed";
    public static final String TEMPORARILY_CLOSED_TEXT = "Temporarily closed";

    /** Case-insensitive match on the stored text; anything else (blank, unknown) is OPEN. */
    public static PlaceBusinessStatus fromStoredStatus(String status) {
        if (status == null || status.isBlank()) {
            return OPEN;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("permanently closed")) {
            return PERMANENTLY_CLOSED;
        }
        if (normalized.contains("temporarily closed")) {
            return TEMPORARILY_CLOSED;
        }
        return OPEN;
    }

    public boolean isClosed() {
        return this != OPEN;
    }
}
