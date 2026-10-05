package com.ds.goroute.localevent.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Time conversions for local events. The API takes instants WITH an offset (or Z) and refuses
 * offset-less times, because a crawler that drops the offset is the classic seven-hour bug for
 * Vietnamese sources. Storage is UTC {@code TIMESTAMP}; day logic uses the event's zone.
 */
public final class LocalEventTimes {

    /** Default event zone and the zone every "Vietnamese day" is computed in. */
    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private LocalEventTimes() {
    }

    /** An ISO-8601 date-time with an offset or Z; empty for anything else (including offset-less). */
    public static Optional<Instant> parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(value.trim(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant());
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    /** A plain ISO date (YYYY-MM-DD), accepted only for all-day events. */
    public static Optional<LocalDate> parseDate(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    public static LocalDateTime toUtc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public static Instant fromUtc(LocalDateTime utc) {
        return utc.toInstant(ZoneOffset.UTC);
    }

    /** UTC instant of local midnight starting {@code date} in {@code zone}. */
    public static LocalDateTime startOfDayUtc(LocalDate date, ZoneId zone) {
        return toUtc(date.atStartOfDay(zone).toInstant());
    }

    /** UTC instant of local 23:59:59 of {@code date} in {@code zone}. */
    public static LocalDateTime endOfDayUtc(LocalDate date, ZoneId zone) {
        return toUtc(date.atTime(23, 59, 59).atZone(zone).toInstant());
    }

    public static LocalDate localDate(Instant instant, ZoneId zone) {
        return instant.atZone(zone).toLocalDate();
    }
}
