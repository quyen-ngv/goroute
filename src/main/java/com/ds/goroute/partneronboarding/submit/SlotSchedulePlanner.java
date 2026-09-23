package com.ds.goroute.partneronboarding.submit;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns a weekly pattern into the departure slots a partner can actually sell.
 *
 * <p>Both branches that need slots describe them weekly, just at different resolutions: an
 * experience lists the times it departs, a service says which hours it is open and lets the
 * treatment length divide them up. Rather than two loops that drift apart, the opening-hours
 * form is reduced to the list-of-times form and both go through the same generator.
 *
 * <p>Pure, and works in local time on purpose: a slot carries its own timezone, and the
 * partner means "10:00 at my shop" regardless of where the server is.
 */
public final class SlotSchedulePlanner {

    /**
     * Refuses to generate more than this in one submit. A typo in the horizon or a
     * fifteen-minute treatment open around the clock would otherwise write tens of thousands
     * of rows that a partner then has to delete by hand.
     */
    public static final int MAX_SLOTS = 2000;

    /** One departure, in the listing's own local time. */
    public record PlannedSlot(LocalDateTime startsAt, LocalDateTime endsAt) {
    }

    /** "Monday to Friday, 09:00–17:00" as the business-hours screen collects it. */
    public record HoursRange(Set<DayOfWeek> days, LocalTime from, LocalTime to) {
        public boolean isUsable() {
            return days != null && !days.isEmpty() && from != null && to != null && from.isBefore(to);
        }
    }

    private SlotSchedulePlanner() {
    }

    /**
     * Departures at the times the partner listed, on the days they listed them.
     *
     * @param weekly          start times per weekday; a day with no times has no departures
     * @param durationMinutes how long one departure runs, used for the end time
     * @param from            first day considered, inclusive
     * @param horizonDays     how many days ahead to generate
     */
    public static List<PlannedSlot> atTimes(Map<DayOfWeek, ? extends Collection<LocalTime>> weekly,
                                            int durationMinutes, LocalDate from, int horizonDays) {
        List<PlannedSlot> slots = new ArrayList<>();
        if (weekly == null || weekly.isEmpty() || durationMinutes <= 0 || from == null || horizonDays <= 0) {
            return slots;
        }
        for (int day = 0; day < horizonDays && slots.size() < MAX_SLOTS; day++) {
            LocalDate date = from.plusDays(day);
            Collection<LocalTime> times = weekly.get(date.getDayOfWeek());
            if (times == null) {
                continue;
            }
            // Sorted and de-duplicated: two screens can offer the same time twice, and a
            // partner reading their calendar should not see a slot listed out of order.
            for (LocalTime time : new TreeSet<>(times)) {
                if (time == null) {
                    continue;
                }
                if (slots.size() >= MAX_SLOTS) {
                    break;
                }
                LocalDateTime startsAt = LocalDateTime.of(date, time);
                slots.add(new PlannedSlot(startsAt, startsAt.plusMinutes(durationMinutes)));
            }
        }
        return slots;
    }

    /**
     * Departures every {@code durationMinutes} inside the opening hours, back to back.
     *
     * <p>A slot that would run past closing is not generated: a 90-minute treatment in a shop
     * that closes at 17:00 has its last start at 15:30, which is what the partner means by
     * "open until five".
     */
    public static List<PlannedSlot> withinHours(List<HoursRange> ranges, int durationMinutes,
                                                LocalDate from, int horizonDays) {
        if (ranges == null || ranges.isEmpty() || durationMinutes <= 0) {
            return List.of();
        }
        Map<DayOfWeek, Set<LocalTime>> weekly = new java.util.EnumMap<>(DayOfWeek.class);
        for (HoursRange range : ranges) {
            if (range == null || !range.isUsable()) {
                continue;
            }
            for (DayOfWeek day : range.days()) {
                if (day == null) {
                    continue;
                }
                Set<LocalTime> times = weekly.computeIfAbsent(day, key -> new TreeSet<>());
                // Counted in minutes-of-day rather than with LocalTime arithmetic, which wraps
                // silently past midnight: a late closing time would otherwise never end the loop.
                int closing = range.to().toSecondOfDay() / 60;
                for (int start = range.from().toSecondOfDay() / 60;
                     start + durationMinutes <= closing;
                     start += durationMinutes) {
                    times.add(LocalTime.of(start / 60, start % 60));
                }
            }
        }
        return atTimes(weekly, durationMinutes, from, horizonDays);
    }
}
