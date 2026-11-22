package com.ds.goroute.meetup;

import com.ds.goroute.meetup.dto.MeetupContextResponse;
import com.ds.goroute.meetup.dto.MeetupDetailResponse;
import com.ds.goroute.meetup.dto.MeetupSummaryResponse;
import com.ds.goroute.meetup.dto.MeetupUpsertRequest;
import com.ds.goroute.type.MeetupJoinPolicy;
import com.ds.goroute.type.MeetupLocationMode;
import com.ds.goroute.type.MeetupTimeMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the JSON names the app ({@code meetup.dart}) reads and sends. Primitive booleans such as
 * {@code locationHidden} / {@code canHost} must keep their names (Lombok's {@code isX()} getters),
 * {@code meetupDate} / {@code today} are plain local dates and times are UTC without offset.
 */
@DisplayName("Meetup JSON contract (app)")
class MeetupJsonContractTest {

    /** As Spring Boot configures it (no custom mapper bean): java.time written as ISO text. */
    private static final ObjectMapper MAPPER = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    private static final List<String> SUMMARY_FIELDS = List.of("id", "host", "title", "category", "locationMode",
            "latitude", "longitude", "radiusM", "locationHidden", "areaLabel", "placeName", "meetupDate", "timeMode",
            "startsAt", "visibleUntil", "timezone", "joinPolicy", "minAge", "maxAge", "maxParticipants",
            "joinedCount", "participantCount", "distanceKm", "status", "effectiveStatus", "viewerRole",
            "viewerStatus", "createdAt");

    /** Through the real text form: valueToTree keeps java.time values as POJO nodes. */
    private static JsonNode json(Object value) throws Exception {
        return MAPPER.readTree(MAPPER.writeValueAsString(value));
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> keys = new TreeSet<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    @Test
    @DisplayName("summary: field names, a local date and an offset-less UTC time")
    void summaryFieldNames() throws Exception {
        MeetupSummaryResponse summary = new MeetupSummaryResponse();
        summary.setLocationHidden(true);
        summary.setMeetupDate(LocalDate.of(2026, 10, 4));
        summary.setVisibleUntil(LocalDateTime.of(2026, 10, 4, 16, 59, 59));

        JsonNode json = json(summary);

        assertThat(keys(json)).containsExactlyInAnyOrderElementsOf(SUMMARY_FIELDS);
        assertThat(json.get("locationHidden").asBoolean()).isTrue();
        assertThat(json.get("meetupDate").asText()).isEqualTo("2026-10-04");
        assertThat(json.get("visibleUntil").asText()).isEqualTo("2026-10-04T16:59:59");
    }

    @Test
    @DisplayName("detail: summary fields plus the sheet's fields")
    void detailFieldNames() throws Exception {
        JsonNode json = json(new MeetupDetailResponse());

        List<String> expected = new ArrayList<>(SUMMARY_FIELDS);
        expected.addAll(List.of("description", "address", "placeId", "participantsVisible", "participantsPreview",
                "pendingRequestCount", "rejectReason", "reviewNote", "resubmitsLeft", "cancelReason", "dataVersion"));
        assertThat(keys(json)).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    @DisplayName("context: canHost and enabled keep their names, today is a local date")
    void contextFieldNames() throws Exception {
        MeetupContextResponse context = MeetupContextResponse.builder()
                .enabled(true).canHost(true).today(LocalDate.of(2026, 10, 4)).build();

        JsonNode json = json(context);

        assertThat(keys(json)).containsExactlyInAnyOrder("enabled", "birthYear", "canHost", "cannotHostReason",
                "maxDaysAhead", "exactMinLeadMinutes", "exactVisibleHours", "areaRadiiM", "categories", "today",
                "timezone");
        assertThat(json.get("canHost").asBoolean()).isTrue();
        assertThat(json.get("today").asText()).isEqualTo("2026-10-04");
    }

    @Test
    @DisplayName("create body exactly as the app's MeetupUpsertRequest.toJson sends it")
    void appUpsertBodyBinds() throws Exception {
        MeetupUpsertRequest request = MAPPER.readValue("""
                {"title":"đi cà phê muối","category":"COFFEE","locationMode":"EXACT",
                 "latitude":21.0285,"longitude":105.8542,
                 "placeId":"0b9c2d4e-1f2a-4b3c-8d4e-5f6a7b8c9d0e","placeName":"Cà phê Giảng",
                 "meetupDate":"2026-10-06","timeMode":"EXACT","startTime":"19:00",
                 "minAge":18,"maxAge":null,"joinPolicy":"APPROVAL","maxParticipants":4,"expectedVersion":2}
                """, MeetupUpsertRequest.class);

        assertThat(request.getLocationMode()).isEqualTo(MeetupLocationMode.EXACT);
        assertThat(request.getLatitude()).isEqualByComparingTo(new BigDecimal("21.0285"));
        assertThat(request.getPlaceId()).hasToString("0b9c2d4e-1f2a-4b3c-8d4e-5f6a7b8c9d0e");
        assertThat(request.getMeetupDate()).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(request.getTimeMode()).isEqualTo(MeetupTimeMode.EXACT);
        assertThat(request.getStartTime()).isEqualTo("19:00");
        assertThat(request.getMaxAge()).isNull();
        assertThat(request.getJoinPolicy()).isEqualTo(MeetupJoinPolicy.APPROVAL);
        assertThat(request.getMaxParticipants()).isEqualTo(4);
        assertThat(request.getExpectedVersion()).isEqualTo(2L);
    }
}
