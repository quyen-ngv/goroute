package com.ds.goroute.service.place;

import com.ds.goroute.dto.PlaceHoursDay;
import com.ds.goroute.dto.PlaceHoursRange;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the structured week a user or operator edits into the Google-style strings
 * {@code places.open_hours} holds, and reads stored strings back into the structured form.
 *
 * <p>Stored shape: {@code {"Monday": ["11 AM–2 PM", "5 PM–11 PM"], "Sunday": ["Closed"]}}, days by
 * full English name, {@code "Open 24 hours"} for a day that never closes. Written with an en dash
 * and plain spaces, both clocks carrying AM/PM; the app's {@code PlaceOpenHoursStatus} and the AI
 * worker read that, and the shared cases in {@code src/test/resources/open_hours_cases.json} pin it.
 *
 * <p>Reading accepts everything Google and the scraper store: hyphen, en/em dash or "to",
 * typographic spaces (U+202F, U+2009), a meridiem written once ("6 - 9:30 PM"), 24-hour clocks,
 * empty padding entries and Private-Use-Area icon glyphs.
 */
public final class PlaceOpenHoursFormatter {

    public static final String CLOSED = "Closed";
    public static final String OPEN_24_HOURS = "Open 24 hours";
    public static final int MAX_RANGES_PER_DAY = 3;
    private static final String DASH = "–";

    private static final Pattern CLOCK_24 = Pattern.compile("^(\\d{1,2}):(\\d{2})$");
    private static final Pattern RANGE = Pattern.compile(
            "^(\\d{1,2}(?::\\d{2})?\\s*(?:AM|PM)?)\\s*(?:[-–—]|to)\\s*(\\d{1,2}(?::\\d{2})?\\s*(?:AM|PM)?)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOCK = Pattern.compile("^(\\d{1,2})(?::(\\d{2}))?\\s*(AM|PM)?$",
            Pattern.CASE_INSENSITIVE);

    private PlaceOpenHoursFormatter() {
    }

    /**
     * The stored map for a full week, Monday first.
     *
     * @throws IllegalArgumentException when a day is missing or repeated, a day has no window or
     *                                  more than {@link #MAX_RANGES_PER_DAY}, or a clock is not HH:mm
     */
    public static Map<String, List<String>> format(List<PlaceHoursDay> week) {
        if (week == null || week.size() != DayOfWeek.values().length) {
            throw new IllegalArgumentException("openHours must list all 7 days");
        }
        Map<DayOfWeek, PlaceHoursDay> byDay = new EnumMap<>(DayOfWeek.class);
        for (PlaceHoursDay day : week) {
            if (day == null || day.getDay() == null) {
                throw new IllegalArgumentException("openHours day is missing");
            }
            if (byDay.put(day.getDay(), day) != null) {
                throw new IllegalArgumentException("openHours lists " + day.getDay() + " twice");
            }
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
            result.put(dayName(dayOfWeek), formatDay(byDay.get(dayOfWeek)));
        }
        return result;
    }

    private static List<String> formatDay(PlaceHoursDay day) {
        if (day.isClosed()) {
            return List.of(CLOSED);
        }
        if (day.isAllDay()) {
            return List.of(OPEN_24_HOURS);
        }
        List<PlaceHoursRange> ranges = day.getRanges() == null ? List.of() : day.getRanges();
        if (ranges.isEmpty() || ranges.size() > MAX_RANGES_PER_DAY) {
            throw new IllegalArgumentException(day.getDay() + " needs 1 to " + MAX_RANGES_PER_DAY
                    + " opening windows, or closed / allDay");
        }
        List<String> values = new ArrayList<>(ranges.size());
        for (PlaceHoursRange range : ranges) {
            if (range == null) {
                throw new IllegalArgumentException(day.getDay() + " has an empty window");
            }
            int from = minuteOf(range.getFrom());
            int to = minuteOf(range.getTo());
            if (from % (24 * 60) == to % (24 * 60)) {
                throw new IllegalArgumentException(day.getDay() + " window opens and closes at the same time");
            }
            values.add(twelveHour(from) + DASH + twelveHour(to));
        }
        return values;
    }

    /** Minutes since midnight of an {@code HH:mm} clock; 24:00 is accepted as midnight. */
    static int minuteOf(String clock) {
        Matcher matcher = clock == null ? null : CLOCK_24.matcher(clock.trim());
        if (matcher == null || !matcher.matches()) {
            throw new IllegalArgumentException("Time '" + clock + "' is not HH:mm");
        }
        int hour = Integer.parseInt(matcher.group(1));
        int minute = Integer.parseInt(matcher.group(2));
        if (minute > 59 || hour > 24 || (hour == 24 && minute != 0)) {
            throw new IllegalArgumentException("Time '" + clock + "' is not a clock time");
        }
        return hour * 60 + minute;
    }

    /** {@code 8 AM}, {@code 8:30 PM}, {@code 12 AM} for midnight, {@code 12 PM} for noon. */
    static String twelveHour(int minuteOfDay) {
        int normalized = minuteOfDay % (24 * 60);
        int hour = normalized / 60;
        int minute = normalized % 60;
        String period = hour < 12 ? "AM" : "PM";
        int shown = hour % 12 == 0 ? 12 : hour % 12;
        return minute == 0
                ? shown + " " + period
                : String.format(Locale.ROOT, "%d:%02d %s", shown, minute, period);
    }

    /**
     * The structured week of stored hours, or null when any day is absent or cannot be read, so a
     * form is only pre-filled with what the place really shows.
     */
    public static List<PlaceHoursDay> parse(Map<String, List<String>> stored) {
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        Map<String, List<String>> byLowerName = new LinkedHashMap<>();
        stored.forEach((key, value) -> {
            if (key != null) {
                byLowerName.put(key.trim().toLowerCase(Locale.ROOT), value);
            }
        });
        List<PlaceHoursDay> week = new ArrayList<>(7);
        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
            List<String> entries = byLowerName.get(dayName(dayOfWeek).toLowerCase(Locale.ROOT));
            PlaceHoursDay day = parseDay(dayOfWeek, entries);
            if (day == null) {
                return null;
            }
            week.add(day);
        }
        return week;
    }

    private static PlaceHoursDay parseDay(DayOfWeek dayOfWeek, List<String> entries) {
        if (entries == null) {
            return null;
        }
        List<String> cleaned = new ArrayList<>();
        for (String entry : entries) {
            String value = clean(entry);
            if (!value.isEmpty()) {
                cleaned.add(value);
            }
        }
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.stream().anyMatch(value -> value.equalsIgnoreCase(OPEN_24_HOURS))) {
            return PlaceHoursDay.builder().day(dayOfWeek).allDay(true).ranges(List.of()).build();
        }
        if (cleaned.size() == 1 && cleaned.get(0).equalsIgnoreCase(CLOSED)) {
            return PlaceHoursDay.builder().day(dayOfWeek).closed(true).ranges(List.of()).build();
        }
        List<PlaceHoursRange> ranges = new ArrayList<>();
        for (String value : cleaned) {
            PlaceHoursRange range = parseRange(value);
            if (range == null || ranges.size() == MAX_RANGES_PER_DAY) {
                return null;
            }
            ranges.add(range);
        }
        return PlaceHoursDay.builder().day(dayOfWeek).ranges(ranges).build();
    }

    /** Typographic spaces to plain ones, icon glyphs (Private Use Area) dropped, trimmed. */
    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\uE000-\\uF8FF]", "")
                .replaceAll("[\\s\\u00A0\\u2007\\u2009\\u202F]+", " ")
                .trim();
    }

    private static PlaceHoursRange parseRange(String value) {
        Matcher matcher = RANGE.matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        Clock start = parseClock(matcher.group(1));
        Clock end = parseClock(matcher.group(2));
        if (start == null || end == null) {
            return null;
        }
        // A shared meridiem is written once ("6 - 9:30 PM"): a bare clock borrows the other side's.
        if (start.period == null && end.period != null) {
            start = new Clock(start.hour, start.minute, end.period);
        } else if (end.period == null && start.period != null) {
            end = new Clock(end.hour, end.minute, start.period);
        }
        Integer from = start.minuteOfDay();
        Integer to = end.minuteOfDay();
        if (from == null || to == null) {
            return null;
        }
        return PlaceHoursRange.builder().from(hhmm(from)).to(hhmm(to)).build();
    }

    private static Clock parseClock(String value) {
        Matcher matcher = CLOCK.matcher(value.trim());
        if (!matcher.matches()) {
            return null;
        }
        int hour = Integer.parseInt(matcher.group(1));
        int minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        if (minute > 59) {
            return null;
        }
        String period = matcher.group(3) == null ? null : matcher.group(3).toUpperCase(Locale.ROOT);
        return new Clock(hour, minute, period);
    }

    private static String hhmm(int minuteOfDay) {
        int normalized = minuteOfDay % (24 * 60);
        return String.format(Locale.ROOT, "%02d:%02d", normalized / 60, normalized % 60);
    }

    /** {@code Monday} … {@code Sunday}, the keys Google and the scraper store. */
    public static String dayName(DayOfWeek day) {
        String name = day.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    private record Clock(int hour, int minute, String period) {
        Integer minuteOfDay() {
            if (period != null) {
                if (hour < 1 || hour > 12) {
                    return null;
                }
                return (hour % 12 + ("PM".equals(period) ? 12 : 0)) * 60 + minute;
            }
            if (hour > 24 || (hour == 24 && minute != 0)) {
                return null;
            }
            return hour * 60 + minute;
        }
    }
}
