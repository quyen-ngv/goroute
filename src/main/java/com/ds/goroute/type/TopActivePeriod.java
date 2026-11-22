package com.ds.goroute.type;

import com.ds.goroute.utils.AppZones;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * The calendar period the Feed's "Top active users" board counts: the current day, week
 * (from Monday), month or year in Vietnam time. One zone for everyone, so every viewer
 * sees the same board.
 */
public enum TopActivePeriod {
    DAY,
    WEEK,
    MONTH,
    YEAR;

    /** Start of the current period as a UTC wall clock, the form {@code TIMESTAMP} columns store. */
    public LocalDateTime startUtc(Instant now) {
        LocalDate today = now.atZone(AppZones.VIETNAM).toLocalDate();
        LocalDate first = switch (this) {
            case DAY -> today;
            case WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> today.withDayOfMonth(1);
            case YEAR -> today.withDayOfYear(1);
        };
        return first.atStartOfDay(AppZones.VIETNAM)
                .withZoneSameInstant(AppZones.UTC)
                .toLocalDateTime();
    }
}
