package com.ds.goroute.service.place;

import com.ds.goroute.dto.PlaceHoursDay;
import com.ds.goroute.dto.PlaceHoursRange;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The stored opening-hours strings, pinned by {@code open_hours_cases.json}, which the app's
 * PlaceOpenHoursStatus tests read too: what the server writes is what the app parses.
 */
@DisplayName("PlaceOpenHoursFormatter")
class PlaceOpenHoursFormatterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static JsonNode cases;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream input = PlaceOpenHoursFormatterTest.class.getClassLoader()
                .getResourceAsStream("open_hours_cases.json")) {
            assertThat(input).as("open_hours_cases.json on the test classpath").isNotNull();
            cases = MAPPER.readTree(input);
        }
    }

    @Test
    void everyFormatCaseProducesTheSharedStrings() {
        for (JsonNode testCase : cases.get("formatCases")) {
            List<PlaceHoursDay> week = week(testCase.get("structured"));
            Map<String, List<String>> expected = MAPPER.convertValue(testCase.get("display"),
                    new TypeReference<Map<String, List<String>>>() {});

            assertThat(PlaceOpenHoursFormatter.format(week))
                    .as(testCase.get("name").asText())
                    .containsExactlyEntriesOf(expected);
        }
    }

    @Test
    void formattedStringsReadBackToTheSameWeek() {
        for (JsonNode testCase : cases.get("formatCases")) {
            List<PlaceHoursDay> week = week(testCase.get("structured"));

            List<PlaceHoursDay> parsed = PlaceOpenHoursFormatter.parse(PlaceOpenHoursFormatter.format(week));

            assertThat(parsed).as(testCase.get("name").asText()).isEqualTo(normalized(week));
        }
    }

    @Test
    void everyParseCaseReadsAsShared() {
        for (JsonNode testCase : cases.get("parseCases")) {
            Map<String, List<String>> stored = MAPPER.convertValue(testCase.get("display"),
                    new TypeReference<Map<String, List<String>>>() {});
            JsonNode expected = testCase.get("structured");

            List<PlaceHoursDay> parsed = PlaceOpenHoursFormatter.parse(stored);

            if (expected == null || expected.isNull()) {
                assertThat(parsed).as(testCase.get("name").asText()).isNull();
            } else {
                assertThat(parsed).as(testCase.get("name").asText()).isEqualTo(week(expected));
            }
        }
    }

    @Test
    void aWeekWithoutSevenDaysIsRefused() {
        List<PlaceHoursDay> week = new ArrayList<>(allDayWeek());
        week.remove(0);

        assertThatThrownBy(() -> PlaceOpenHoursFormatter.format(week))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moreThanThreeWindowsOrAnEmptyWindowIsRefused() {
        List<PlaceHoursDay> week = new ArrayList<>(allDayWeek());
        week.set(0, PlaceHoursDay.builder().day(DayOfWeek.MONDAY).ranges(List.of(
                range("06:00", "07:00"), range("08:00", "09:00"),
                range("10:00", "11:00"), range("12:00", "13:00"))).build());
        assertThatThrownBy(() -> PlaceOpenHoursFormatter.format(week)).isInstanceOf(IllegalArgumentException.class);

        week.set(0, PlaceHoursDay.builder().day(DayOfWeek.MONDAY).ranges(List.of(range("08:00", "08:00"))).build());
        assertThatThrownBy(() -> PlaceOpenHoursFormatter.format(week)).isInstanceOf(IllegalArgumentException.class);

        week.set(0, PlaceHoursDay.builder().day(DayOfWeek.MONDAY).ranges(List.of(range("8am", "09:00"))).build());
        assertThatThrownBy(() -> PlaceOpenHoursFormatter.format(week)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void twentyFourHundredIsMidnight() {
        assertThat(PlaceOpenHoursFormatter.twelveHour(PlaceOpenHoursFormatter.minuteOf("24:00"))).isEqualTo("12 AM");
        assertThat(PlaceOpenHoursFormatter.twelveHour(PlaceOpenHoursFormatter.minuteOf("12:05"))).isEqualTo("12:05 PM");
    }

    private static List<PlaceHoursDay> week(JsonNode node) {
        return MAPPER.convertValue(node, new TypeReference<List<PlaceHoursDay>>() {});
    }

    /** What a week reads back as: closed and all-day days without windows, 24:00 written 00:00. */
    private static List<PlaceHoursDay> normalized(List<PlaceHoursDay> week) {
        List<PlaceHoursDay> result = new ArrayList<>();
        for (PlaceHoursDay day : week) {
            List<PlaceHoursRange> ranges = day.isClosed() || day.isAllDay() ? List.of()
                    : day.getRanges().stream()
                            .map(range -> range(range.getFrom().replace("24:00", "00:00"),
                                    range.getTo().replace("24:00", "00:00")))
                            .toList();
            result.add(PlaceHoursDay.builder().day(day.getDay()).closed(day.isClosed())
                    .allDay(!day.isClosed() && day.isAllDay()).ranges(ranges).build());
        }
        return result;
    }

    private static List<PlaceHoursDay> allDayWeek() {
        List<PlaceHoursDay> week = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            week.add(PlaceHoursDay.builder().day(day).allDay(true).ranges(List.of()).build());
        }
        return week;
    }

    private static PlaceHoursRange range(String from, String to) {
        return PlaceHoursRange.builder().from(from).to(to).build();
    }
}
