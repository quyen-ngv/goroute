package com.ds.goroute.localevent;

import com.ds.goroute.localevent.dto.AdminApiKeyCreateRequest;
import com.ds.goroute.localevent.dto.LocalEventDetailResponse;
import com.ds.goroute.localevent.dto.LocalEventImportBatchResponse;
import com.ds.goroute.localevent.dto.LocalEventSummaryResponse;
import com.ds.goroute.localevent.dto.LocalEventUpsertRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the JSON names the app ({@code local_event.dart}) and the console ({@code eventApi.ts}) read
 * and send. Lombok + Jackson decide these names (e.g. a {@code Boolean isFree} stays
 * {@code "isFree"}, a primitive {@code boolean} would become {@code "free"}), so a refactor of a
 * field type can silently rename a wire field; the request DTOs ignore unknown fields, so a renamed
 * request field would be dropped without an error. Uses the mapper Spring Boot builds.
 */
@DisplayName("Local event JSON contract (app + console)")
class LocalEventJsonContractTest {

    /** As Spring Boot configures it (no custom mapper bean): java.time written as ISO text. */
    private static final ObjectMapper MAPPER = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    private static final List<String> SUMMARY_FIELDS = List.of("id", "title", "category", "startAt", "endAt",
            "timezone", "allDay", "eventStatus", "venueName", "address", "latitude", "longitude", "distanceKm",
            "coverUrl", "isFree", "priceMin", "priceMax", "currency", "ticketAvailability", "sessionCount");

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
    @DisplayName("public summary: the field names the app reads, times UTC without offset")
    void summaryFieldNames() throws Exception {
        LocalEventSummaryResponse summary = new LocalEventSummaryResponse();
        summary.setIsFree(true);
        summary.setStartAt(LocalDateTime.of(2026, 11, 7, 12, 30));

        JsonNode json = json(summary);

        assertThat(keys(json)).containsExactlyInAnyOrderElementsOf(SUMMARY_FIELDS);
        assertThat(json.get("isFree").asBoolean()).isTrue();
        assertThat(json.get("startAt").asText()).isEqualTo("2026-11-07T12:30:00");
    }

    @Test
    @DisplayName("public detail: summary fields plus the detail fields the app reads")
    void detailFieldNames() throws Exception {
        JsonNode json = json(new LocalEventDetailResponse());

        List<String> expected = new ArrayList<>(SUMMARY_FIELDS);
        expected.addAll(List.of("summary", "description", "language", "tags", "sessions", "scheduleNote",
                "lunarDateText", "previousStartAt", "attendanceMode", "onlineUrl", "placeId", "provinceCode",
                "wardCode", "priceNote", "ticketUrl", "organizerName", "organizerUrl", "performers", "minAge",
                "endAtEstimated", "source", "sourceUrl", "updatedAt", "images"));
        assertThat(keys(json)).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    @DisplayName("console upsert body: isFree and nested names bind (unknown fields are ignored silently)")
    void upsertRequestBinds() throws Exception {
        LocalEventUpsertRequest request = MAPPER.readValue("""
                {"title":"Đêm nhạc","category":"MUSIC","startAt":"2026-11-07T19:30:00+07:00","isFree":true,
                 "venue":{"name":"Nhà hát","latitude":21.02,"longitude":105.85,"placeId":null},
                 "organizer":{"name":"CLB","url":"https://clb.example"},
                 "sessions":[{"startAt":"2026-11-07T19:30:00+07:00","label":"Suất 1","status":"SCHEDULED"}],
                 "images":[{"url":"https://cdn.example/a.jpg","credit":"BTC"}],
                 "publishStatus":"PUBLISHED","expectedVersion":3}
                """, LocalEventUpsertRequest.class);

        assertThat(request.getIsFree()).isTrue();
        assertThat(request.getVenue().getName()).isEqualTo("Nhà hát");
        assertThat(request.getOrganizer().getUrl()).isEqualTo("https://clb.example");
        assertThat(request.getSessions()).singleElement()
                .satisfies(session -> assertThat(session.getLabel()).isEqualTo("Suất 1"));
        assertThat(request.getImages()).singleElement()
                .satisfies(image -> assertThat(image.getCredit()).isEqualTo("BTC"));
        assertThat(request.getPublishStatus()).isEqualTo("PUBLISHED");
        assertThat(request.getExpectedVersion()).isEqualTo(3L);
    }

    @Test
    @DisplayName("import batch row: dryRun keeps its name")
    void importBatchFieldNames() throws Exception {
        JsonNode json = json(new LocalEventImportBatchResponse());

        assertThat(keys(json)).containsExactlyInAnyOrder("id", "actorUserId", "actorName", "apiKeyId", "apiKeyName",
                "dryRun", "total", "created", "updated", "unchanged", "skipped", "failed", "createdAt");
    }

    @Test
    @DisplayName("API key create: the console's expiresAt with a +07:00 offset binds")
    void apiKeyCreateBinds() throws Exception {
        AdminApiKeyCreateRequest request = MAPPER.readValue(
                "{\"name\":\"crawler\",\"expiresAt\":\"2027-01-31T23:59:59+07:00\"}", AdminApiKeyCreateRequest.class);

        assertThat(request.getExpiresAt().toInstant())
                .isEqualTo(LocalDateTime.of(2027, 1, 31, 16, 59, 59).toInstant(ZoneOffset.UTC));
    }
}
