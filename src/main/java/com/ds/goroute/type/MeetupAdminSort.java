package com.ds.goroute.type;

import java.util.Locale;

/**
 * Orderings of the console meetup list. {@code DEADLINE} is the review queue's order: the
 * meetup that leaves the map soonest first, since a late review is a useless one.
 */
public enum MeetupAdminSort {
    DEADLINE,
    SUBMITTED_AT;

    /** Reads {@code deadline} / {@code submittedAt} (any case, with or without underscore). */
    public static MeetupAdminSort fromWire(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace("_", "").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "deadline" -> DEADLINE;
            case "submittedat" -> SUBMITTED_AT;
            default -> throw new IllegalArgumentException("Unknown sort: " + value);
        };
    }
}
