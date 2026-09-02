package com.ds.goroute.utils;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IcalWriterTest {

    private static final LocalDateTime STAMP = LocalDateTime.of(2026, 9, 1, 8, 30, 0);

    @Test
    void rendersValidCalendarWithAllDayEvents() {
        String ics = IcalWriter.render("Sea View Hotel - Deluxe", List.of(
                new IcalWriter.Event("b1@goroute", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12), "Booked - HB-1234"),
                new IcalWriter.Event("closed-2026-09-20-r1@goroute", LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 21), "Closed")),
                STAMP);

        String[] lines = ics.split("\r\n", -1);
        assertEquals("BEGIN:VCALENDAR", lines[0]);
        assertEquals("VERSION:2.0", lines[1]);
        assertEquals("PRODID:" + IcalWriter.PRODID, lines[2]);
        assertTrue(ics.contains("X-WR-CALNAME:Sea View Hotel - Deluxe\r\n"));
        assertTrue(ics.contains("BEGIN:VEVENT\r\nUID:b1@goroute\r\nDTSTAMP:20260901T083000Z\r\n"
                + "DTSTART;VALUE=DATE:20260910\r\nDTEND;VALUE=DATE:20260912\r\nSUMMARY:Booked - HB-1234\r\n"));
        assertTrue(ics.contains("UID:closed-2026-09-20-r1@goroute\r\n"));
        assertTrue(ics.contains("SUMMARY:Closed\r\n"));
        assertEquals(2, ics.split("END:VEVENT").length - 1);
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"));
        // every line break is CRLF: no bare LF anywhere
        assertFalse(ics.replace("\r\n", "").contains("\n"));
    }

    @Test
    void escapesTextAndFoldsLongLinesAt75Octets() {
        String longSummary = "Booked - " + "x".repeat(120) + ", semi;colon\\ and\nnewline";
        String ics = IcalWriter.render(null, List.of(
                new IcalWriter.Event("u@goroute", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), longSummary)), STAMP);

        for (String line : ics.split("\r\n")) {
            assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75, "line exceeds 75 octets: " + line);
        }
        // unfold and check the escaped payload survived intact
        String unfolded = ics.replace("\r\n ", "");
        assertTrue(unfolded.contains("SUMMARY:Booked - " + "x".repeat(120) + "\\, semi\\;colon\\\\ and\\nnewline\r\n"));
        assertFalse(ics.contains("X-WR-CALNAME"));
    }

    @Test
    void rejectsEventsWhoseEndIsNotAfterStart() {
        LocalDate day = LocalDate.of(2026, 3, 3);
        assertThrows(IllegalArgumentException.class, () -> new IcalWriter.Event("u@goroute", day, day, "Booked"));
        assertThrows(IllegalArgumentException.class, () -> new IcalWriter.Event(" ", day, day.plusDays(1), "Booked"));
    }

    @Test
    void emptyCalendarIsStillWellFormed() {
        String ics = IcalWriter.render("Empty", List.of(), STAMP);
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"));
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"));
        assertFalse(ics.contains("VEVENT"));
    }
}
