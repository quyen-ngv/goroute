package com.ds.goroute.utils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Minimal RFC 5545 writer for all-day availability calendars. Pure: no clock, no I/O.
 *
 * <p>Output uses CRLF line endings, folds lines longer than 75 octets and escapes text values,
 * which is what the strict parsers (Google Calendar, Airbnb, Booking.com importers) require.
 */
public final class IcalWriter {

    public static final String PRODID = "-//GoRoute//Marketplace Calendar//EN";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final String CRLF = "\r\n";
    private static final int MAX_LINE_OCTETS = 75;

    private IcalWriter() {}

    /**
     * An all-day event: {@code endExclusive} is the first day the room is free again, as iCal
     * DTEND for VALUE=DATE is exclusive.
     */
    public record Event(String uid, LocalDate start, LocalDate endExclusive, String summary) {
        public Event {
            if (uid == null || uid.isBlank()) throw new IllegalArgumentException("uid is required");
            if (start == null || endExclusive == null) throw new IllegalArgumentException("dates are required");
            if (!endExclusive.isAfter(start)) throw new IllegalArgumentException("endExclusive must be after start");
        }
    }

    /**
     * @param calendarName shown by clients as the calendar title (X-WR-CALNAME)
     * @param events       already ordered the way the caller wants them emitted
     * @param stampUtc     value for DTSTAMP on every event (UTC)
     */
    public static String render(String calendarName, List<Event> events, LocalDateTime stampUtc) {
        StringBuilder out = new StringBuilder(256 + events.size() * 160);
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:" + PRODID);
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:PUBLISH");
        if (calendarName != null && !calendarName.isBlank()) {
            line(out, "X-WR-CALNAME:" + escapeText(calendarName));
        }
        String stamp = STAMP.format(stampUtc);
        for (Event event : events) {
            line(out, "BEGIN:VEVENT");
            line(out, "UID:" + event.uid());
            line(out, "DTSTAMP:" + stamp);
            line(out, "DTSTART;VALUE=DATE:" + DATE.format(event.start()));
            line(out, "DTEND;VALUE=DATE:" + DATE.format(event.endExclusive()));
            line(out, "SUMMARY:" + escapeText(event.summary() == null ? "" : event.summary()));
            line(out, "TRANSP:OPAQUE");
            line(out, "END:VEVENT");
        }
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    /** RFC 5545 §3.3.11: backslash, semicolon, comma and newline are escaped in TEXT values. */
    static String escapeText(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case ';' -> sb.append("\\;");
                case ',' -> sb.append("\\,");
                case '\n' -> sb.append("\\n");
                case '\r' -> { }
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** RFC 5545 §3.1: fold at 75 octets (not chars), continuation lines start with one space. */
    private static void line(StringBuilder out, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_LINE_OCTETS) {
            out.append(content).append(CRLF);
            return;
        }
        int offset = 0;
        boolean first = true;
        while (offset < bytes.length) {
            int room = first ? MAX_LINE_OCTETS : MAX_LINE_OCTETS - 1;
            int end = Math.min(bytes.length, offset + room);
            // never split a multi-byte UTF-8 sequence: back up to a character boundary
            while (end < bytes.length && (bytes[end] & 0xC0) == 0x80) {
                end--;
            }
            if (!first) {
                out.append(' ');
            }
            out.append(new String(bytes, offset, end - offset, StandardCharsets.UTF_8)).append(CRLF);
            offset = end;
            first = false;
        }
    }
}
