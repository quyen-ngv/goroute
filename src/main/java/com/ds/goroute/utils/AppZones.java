package com.ds.goroute.utils;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Time zones the app reasons about.
 *
 * <ul>
 *   <li>Stored timestamps ({@code TIMESTAMP} columns, {@code LocalDateTime} fields) are UTC wall
 *       clocks. The JVM default is pinned to UTC at startup so {@code LocalDateTime.now()} agrees.</li>
 *   <li>Wall-clock times at a place (itinerary, events, hotels) use that place's zone.</li>
 *   <li>Per-user calendar days (daily quotas) use the user's zone, see
 *       {@link com.ds.goroute.config.filter.ClientTimeZoneFilter}.</li>
 * </ul>
 */
public final class AppZones {

    public static final ZoneId UTC = ZoneOffset.UTC;

    /** Fallback when neither the place nor the user has a known zone (most existing data is Vietnamese). */
    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private AppZones() {
    }

    /** Parses an IANA zone id; null for blank, malformed or fixed-offset-only ids we do not accept. */
    public static ZoneId parseOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String id = raw.trim();
        if (id.isEmpty() || id.length() > 64) {
            return null;
        }
        try {
            return ZoneId.of(id);
        } catch (DateTimeException e) {
            return null;
        }
    }

    public static ZoneId parseOr(String raw, ZoneId fallback) {
        ZoneId zone = parseOrNull(raw);
        return zone != null ? zone : fallback;
    }
}
