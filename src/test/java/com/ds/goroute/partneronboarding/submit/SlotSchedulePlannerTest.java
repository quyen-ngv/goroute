package com.ds.goroute.partneronboarding.submit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SlotSchedulePlanner")
class SlotSchedulePlannerTest {

    /** A Monday, so weekday arithmetic in the assertions is readable. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 21);

    @Nested
    @DisplayName("when the partner listed departure times")
    class AtTimes {

        @Test
        @DisplayName("generates one departure per listed time, only on the listed days")
        void generatesListedTimesOnListedDays() {
            List<SlotSchedulePlanner.PlannedSlot> slots = SlotSchedulePlanner.atTimes(
                    Map.of(DayOfWeek.MONDAY, List.of(LocalTime.of(9, 0), LocalTime.of(14, 0))),
                    90, MONDAY, 14);

            assertThat(slots).hasSize(4);
            assertThat(slots).extracting(slot -> slot.startsAt().getDayOfWeek())
                    .containsOnly(DayOfWeek.MONDAY);
            assertThat(slots.get(0).startsAt()).isEqualTo(MONDAY.atTime(9, 0));
            assertThat(slots.get(0).endsAt()).isEqualTo(MONDAY.atTime(10, 30));
        }

        @Test
        @DisplayName("orders and de-duplicates the times a screen may have sent twice")
        void sortsAndDeduplicates() {
            List<SlotSchedulePlanner.PlannedSlot> slots = SlotSchedulePlanner.atTimes(
                    Map.of(DayOfWeek.MONDAY, List.of(LocalTime.of(14, 0), LocalTime.of(9, 0), LocalTime.of(9, 0))),
                    60, MONDAY, 1);

            assertThat(slots).extracting(slot -> slot.startsAt().toLocalTime())
                    .containsExactly(LocalTime.of(9, 0), LocalTime.of(14, 0));
        }

        @Test
        @DisplayName("generates nothing without a pattern, a duration or a horizon")
        void refusesIncompleteInput() {
            assertThat(SlotSchedulePlanner.atTimes(Map.of(), 60, MONDAY, 30)).isEmpty();
            assertThat(SlotSchedulePlanner.atTimes(
                    Map.of(DayOfWeek.MONDAY, List.of(LocalTime.NOON)), 0, MONDAY, 30)).isEmpty();
            assertThat(SlotSchedulePlanner.atTimes(
                    Map.of(DayOfWeek.MONDAY, List.of(LocalTime.NOON)), 60, MONDAY, 0)).isEmpty();
        }

        @Test
        @DisplayName("stops at the cap rather than writing tens of thousands of rows")
        void stopsAtTheCap() {
            Map<DayOfWeek, List<LocalTime>> everyHourEveryDay = Map.of(
                    DayOfWeek.MONDAY, hourly(), DayOfWeek.TUESDAY, hourly(), DayOfWeek.WEDNESDAY, hourly(),
                    DayOfWeek.THURSDAY, hourly(), DayOfWeek.FRIDAY, hourly(), DayOfWeek.SATURDAY, hourly(),
                    DayOfWeek.SUNDAY, hourly());

            List<SlotSchedulePlanner.PlannedSlot> slots =
                    SlotSchedulePlanner.atTimes(everyHourEveryDay, 60, MONDAY, 365);

            assertThat(slots).hasSize(SlotSchedulePlanner.MAX_SLOTS);
        }

        private List<LocalTime> hourly() {
            return java.util.stream.IntStream.range(0, 24).mapToObj(hour -> LocalTime.of(hour, 0)).toList();
        }
    }

    @Nested
    @DisplayName("when the partner gave opening hours")
    class WithinHours {

        @Test
        @DisplayName("fills the opening hours back to back with the offering's own length")
        void dividesOpeningHoursByDuration() {
            List<SlotSchedulePlanner.PlannedSlot> slots = SlotSchedulePlanner.withinHours(
                    List.of(new SlotSchedulePlanner.HoursRange(Set.of(DayOfWeek.MONDAY),
                            LocalTime.of(9, 0), LocalTime.of(12, 0))),
                    60, MONDAY, 1);

            assertThat(slots).extracting(slot -> slot.startsAt().toLocalTime())
                    .containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0));
        }

        @Test
        @DisplayName("never starts an appointment that would run past closing time")
        void neverOverrunsClosingTime() {
            List<SlotSchedulePlanner.PlannedSlot> slots = SlotSchedulePlanner.withinHours(
                    List.of(new SlotSchedulePlanner.HoursRange(Set.of(DayOfWeek.MONDAY),
                            LocalTime.of(9, 0), LocalTime.of(17, 0))),
                    90, MONDAY, 1);

            assertThat(slots).isNotEmpty();
            assertThat(slots).allSatisfy(slot ->
                    assertThat(slot.endsAt().toLocalTime()).isBeforeOrEqualTo(LocalTime.of(17, 0)));
            // 09:00, 10:30, 12:00, 13:30, 15:00 — the next would end at 18:00.
            assertThat(slots.get(slots.size() - 1).startsAt().toLocalTime()).isEqualTo(LocalTime.of(15, 0));
        }

        @Test
        @DisplayName("terminates on hours that reach the end of the day")
        void terminatesOnLateClosingHours() {
            List<SlotSchedulePlanner.PlannedSlot> slots = SlotSchedulePlanner.withinHours(
                    List.of(new SlotSchedulePlanner.HoursRange(Set.of(DayOfWeek.MONDAY),
                            LocalTime.of(22, 0), LocalTime.of(23, 59))),
                    60, MONDAY, 1);

            assertThat(slots).extracting(slot -> slot.startsAt().toLocalTime())
                    .containsExactly(LocalTime.of(22, 0));
        }

        @Test
        @DisplayName("ignores a range with no days, no times, or an end before its start")
        void ignoresUnusableRanges() {
            List<SlotSchedulePlanner.HoursRange> broken = java.util.Arrays.asList(
                    new SlotSchedulePlanner.HoursRange(Set.of(), LocalTime.of(9, 0), LocalTime.of(17, 0)),
                    new SlotSchedulePlanner.HoursRange(Set.of(DayOfWeek.MONDAY), null, LocalTime.of(17, 0)),
                    new SlotSchedulePlanner.HoursRange(Set.of(DayOfWeek.MONDAY), LocalTime.of(17, 0), LocalTime.of(9, 0)),
                    null);

            assertThat(SlotSchedulePlanner.withinHours(broken, 60, MONDAY, 7)).isEmpty();
        }

        @Test
        @DisplayName("merges two ranges on the same day without duplicating a start time")
        void mergesOverlappingRanges() {
            List<SlotSchedulePlanner.PlannedSlot> slots = SlotSchedulePlanner.withinHours(
                    List.of(new SlotSchedulePlanner.HoursRange(Set.of(DayOfWeek.MONDAY),
                                    LocalTime.of(9, 0), LocalTime.of(11, 0)),
                            new SlotSchedulePlanner.HoursRange(Set.of(DayOfWeek.MONDAY),
                                    LocalTime.of(9, 0), LocalTime.of(10, 0))),
                    60, MONDAY, 1);

            assertThat(slots).extracting(slot -> slot.startsAt().toLocalTime())
                    .containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0));
        }
    }
}
