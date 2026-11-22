package com.ds.goroute.localevent;

import com.ds.goroute.localevent.dto.LocalEventFieldError;
import com.ds.goroute.localevent.dto.LocalEventImageRequest;
import com.ds.goroute.localevent.dto.LocalEventImportItemRequest;
import com.ds.goroute.localevent.dto.LocalEventSessionRequest;
import com.ds.goroute.localevent.dto.LocalEventVenueRequest;
import com.ds.goroute.localevent.service.LocalEventNormalizer;
import com.ds.goroute.localevent.service.LocalEventText;
import com.ds.goroute.localevent.service.NormalizedLocalEvent;
import com.ds.goroute.service.GeoService;
import com.ds.goroute.type.LocalEventFieldErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.ds.goroute.localevent.LocalEventTestData.NOW;
import static com.ds.goroute.localevent.LocalEventTestData.concert;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalEventNormalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final LocalEventNormalizer normalizer =
            new LocalEventNormalizer(LocalEventTestData.geoEverywhere(), objectMapper);

    // --- time ------------------------------------------------------------------------------

    @Test
    void offsetTimesAreStoredInUtc() {
        NormalizedLocalEvent event = normalize(concert());

        assertThat(event.isValid()).isTrue();
        assertThat(event.getStartAt()).isEqualTo(LocalDateTime.of(2026, 11, 7, 12, 30));
        assertThat(event.getEndAt()).isEqualTo(LocalDateTime.of(2026, 11, 7, 15, 0));
        assertThat(event.isEndAtEstimated()).isFalse();
        assertThat(event.getTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(event.getProvinceCode()).isEqualTo("01");
        assertThat(event.getWardCode()).isEqualTo("00004");
    }

    @Test
    void zuluTimesAreAcceptedAsUtc() {
        LocalEventImportItemRequest item = concert();
        item.setStartAt("2026-11-07T12:30:00Z");
        item.setEndAt("2026-11-07T15:00:00.000Z");

        NormalizedLocalEvent event = normalize(item);

        assertThat(event.isValid()).isTrue();
        assertThat(event.getStartAt()).isEqualTo(LocalDateTime.of(2026, 11, 7, 12, 30));
        assertThat(event.getEndAt()).isEqualTo(LocalDateTime.of(2026, 11, 7, 15, 0));
    }

    @Test
    void offsetLessTimesAreRefused() {
        LocalEventImportItemRequest item = concert();
        item.setStartAt("2026-11-07T19:30:00");

        NormalizedLocalEvent event = normalize(item);

        assertThat(codes(event, "startAt")).containsExactly(LocalEventFieldErrorCode.INVALID_FORMAT);
    }

    @Test
    void aMissingEndIsThreeHoursAfterTheStartAndMarkedEstimated() {
        LocalEventImportItemRequest item = concert();
        item.setEndAt(null);

        NormalizedLocalEvent event = normalize(item);

        assertThat(event.getEndAt()).isEqualTo(LocalDateTime.of(2026, 11, 7, 15, 30));
        assertThat(event.isEndAtEstimated()).isTrue();
    }

    @Test
    void anAllDayEventSpansTheLocalDaysFromMidnightToMidnight() {
        LocalEventImportItemRequest item = concert();
        item.setAllDay(true);
        item.setStartAt("2026-11-07");
        item.setEndAt("2026-11-09T00:00:00+07:00");

        NormalizedLocalEvent event = normalize(item);

        assertThat(event.isValid()).isTrue();
        assertThat(event.isAllDay()).isTrue();
        // 2026-11-07 00:00 in Vietnam = 2026-11-06 17:00 UTC; 2026-11-09 23:59:59 local = 16:59:59 UTC.
        assertThat(event.getStartAt()).isEqualTo(LocalDateTime.of(2026, 11, 6, 17, 0));
        assertThat(event.getEndAt()).isEqualTo(LocalDateTime.of(2026, 11, 9, 16, 59, 59));
    }

    @Test
    void anAllDayEventWithoutAnEndLastsItsOneLocalDay() {
        LocalEventImportItemRequest item = concert();
        item.setAllDay(true);
        item.setStartAt("2026-11-07T10:00:00Z");
        item.setEndAt(null);

        NormalizedLocalEvent event = normalize(item);

        assertThat(event.getStartAt()).isEqualTo(LocalDateTime.of(2026, 11, 6, 17, 0));
        assertThat(event.getEndAt()).isEqualTo(LocalDateTime.of(2026, 11, 7, 16, 59, 59));
    }

    @Test
    void sessionsDefineTheSpanOfTheEvent() {
        LocalEventImportItemRequest item = concert();
        item.setSessions(List.of(
                LocalEventSessionRequest.builder().startAt("2026-11-09T19:00:00+07:00").label("Đêm 2").build(),
                LocalEventSessionRequest.builder().startAt("2026-11-08T19:00:00+07:00")
                        .endAt("2026-11-08T21:00:00+07:00").label("Đêm 1").status("cancelled").build()));

        NormalizedLocalEvent event = normalize(item);

        assertThat(event.isValid()).isTrue();
        assertThat(event.getSessions()).extracting(NormalizedLocalEvent.Session::label).containsExactly("Đêm 1", "Đêm 2");
        assertThat(event.getSessions().get(0).status()).isEqualTo("CANCELLED");
        assertThat(event.getStartAt()).isEqualTo(LocalDateTime.of(2026, 11, 8, 12, 0));
        // The last session had no end: start + 3 h.
        assertThat(event.getEndAt()).isEqualTo(LocalDateTime.of(2026, 11, 9, 15, 0));
    }

    @Test
    void anEndBeforeTheStartIsOutOfRange() {
        LocalEventImportItemRequest item = concert();
        item.setEndAt("2026-11-07T18:00:00+07:00");

        assertThat(codes(normalize(item), "endAt")).containsExactly(LocalEventFieldErrorCode.OUT_OF_RANGE);
    }

    @Test
    void anEventThatHasEndedIsRefusedOnImportButStillEditable() {
        LocalEventImportItemRequest item = concert();
        item.setStartAt("2026-10-01T19:00:00+07:00");
        item.setEndAt("2026-10-01T21:00:00+07:00");

        assertThat(codes(normalize(item), "endAt")).containsExactly(LocalEventFieldErrorCode.EVENT_ENDED);
        assertThat(normalizer.normalize(item, LocalEventNormalizer.Mode.ADMIN_UPDATE, NOW).isValid()).isTrue();
    }

    // --- validation codes ------------------------------------------------------------------

    @Test
    void requiredFieldsAreReportedTogether() {
        LocalEventImportItemRequest item = new LocalEventImportItemRequest();

        NormalizedLocalEvent event = normalize(item);

        assertThat(event.getErrors())
                .filteredOn(error -> error.getCode() == LocalEventFieldErrorCode.REQUIRED)
                .extracting(LocalEventFieldError::getField)
                .contains("source", "externalId", "title", "category", "startAt", "venue");
    }

    @Test
    void enumsAreCaseInsensitiveButUnknownValuesAreInvalid() {
        LocalEventImportItemRequest item = concert();
        item.setCategory("music");
        item.setTicketAvailability("SOMETIMES");

        NormalizedLocalEvent event = normalize(item);

        assertThat(codes(event, "category")).isEmpty();
        assertThat(codes(event, "ticketAvailability")).containsExactly(LocalEventFieldErrorCode.INVALID_ENUM);
    }

    @Test
    void textLimitsAndUrlsAreChecked() {
        LocalEventImportItemRequest item = concert();
        item.setTitle("x".repeat(201));
        item.setTicketUrl("http://ticketbox.vn/evt-123");
        item.setSource("TicketBox!");
        item.setImages(Collections.nCopies(11, LocalEventImageRequest.builder().url("https://a.example/x.jpg").build()));

        NormalizedLocalEvent event = normalize(item);

        assertThat(codes(event, "title")).containsExactly(LocalEventFieldErrorCode.TOO_LONG);
        assertThat(codes(event, "ticketUrl")).containsExactly(LocalEventFieldErrorCode.INVALID_URL);
        assertThat(codes(event, "source")).containsExactly(LocalEventFieldErrorCode.INVALID_FORMAT);
        assertThat(codes(event, "images")).containsExactly(LocalEventFieldErrorCode.OUT_OF_RANGE);
    }

    @Test
    void pricesMustBeOrderedAndAFreeEventHasNoPrice() {
        LocalEventImportItemRequest reversed = concert();
        reversed.setPriceMin(500000L);
        reversed.setPriceMax(100000L);
        LocalEventImportItemRequest free = concert();
        free.setIsFree(true);

        assertThat(codes(normalize(reversed), "priceMax")).containsExactly(LocalEventFieldErrorCode.OUT_OF_RANGE);
        assertThat(codes(normalize(free), "isFree")).containsExactly(LocalEventFieldErrorCode.OUT_OF_RANGE);
    }

    @Test
    void aVenueOutsideVietnamIsRefusedBeforeTheWardLookup() {
        GeoService geo = mock(GeoService.class);
        LocalEventNormalizer strict = new LocalEventNormalizer(geo, objectMapper);
        LocalEventImportItemRequest item = concert();
        item.setVenue(LocalEventVenueRequest.builder().name("Bangkok").latitude(new BigDecimal("13.75"))
                .longitude(new BigDecimal("100.5")).build());

        NormalizedLocalEvent event = strict.normalize(item, LocalEventNormalizer.Mode.IMPORT, NOW);

        assertThat(codes(event, "venue")).containsExactly(LocalEventFieldErrorCode.OUTSIDE_VIETNAM);
        verify(geo, never()).resolveWard(any(), any());
    }

    @Test
    void aVenueInsideTheBoxButInNoWardIsOutsideVietnam() {
        GeoService geo = mock(GeoService.class);
        when(geo.resolveWard(any(), any())).thenReturn(Optional.empty());
        LocalEventNormalizer strict = new LocalEventNormalizer(geo, objectMapper);

        NormalizedLocalEvent event = strict.normalize(concert(), LocalEventNormalizer.Mode.IMPORT, NOW);

        assertThat(codes(event, "venue")).containsExactly(LocalEventFieldErrorCode.OUTSIDE_VIETNAM);
    }

    @Test
    void previousStartIsOnlyForARescheduledEvent() {
        LocalEventImportItemRequest item = concert();
        item.setPreviousStartAt("2026-10-30T19:30:00+07:00");

        assertThat(codes(normalize(item), "previousStartAt")).containsExactly(LocalEventFieldErrorCode.OUT_OF_RANGE);

        item.setEventStatus("RESCHEDULED");
        NormalizedLocalEvent rescheduled = normalize(item);
        assertThat(rescheduled.isValid()).isTrue();
        assertThat(rescheduled.getPreviousStartAt()).isEqualTo(LocalDateTime.of(2026, 10, 30, 12, 30));
    }

    @Test
    void hiddenCannotBeRequestedAsAPublishStatus() {
        LocalEventImportItemRequest item = concert();
        item.setPublishStatus("HIDDEN");

        assertThat(codes(normalize(item), "publishStatus")).containsExactly(LocalEventFieldErrorCode.INVALID_ENUM);
    }

    // --- hash and text ---------------------------------------------------------------------

    @Test
    void theContentHashIgnoresSourceMetadataButNotContent() {
        LocalEventImportItemRequest first = concert();
        LocalEventImportItemRequest metadataOnly = concert();
        metadataOnly.setSourceUpdatedAt("2026-10-03T00:00:00Z");
        metadataOnly.setExtractedBy("crawler-v2");
        metadataOnly.setPublishStatus("PUBLISHED");
        metadataOnly.setRawPayload(objectMapper.createObjectNode().put("html", "<p>"));
        LocalEventImportItemRequest changed = concert();
        changed.setPriceMax(1500000L);

        String hash = normalize(first).getContentHash();

        assertThat(hash).hasSize(64);
        assertThat(normalize(metadataOnly).getContentHash()).isEqualTo(hash);
        assertThat(normalize(changed).getContentHash()).isNotEqualTo(hash);
    }

    @Test
    void titlesAreNormalisedForTheDuplicateCheck() {
        assertThat(LocalEventText.normalizeTitle("  Lễ hội   ĐỀN Hùng  ")).isEqualTo("le hoi den hung");
        assertThat(normalize(concert()).getTitleNorm()).isEqualTo("dem nhac trinh cong son");
    }

    private NormalizedLocalEvent normalize(LocalEventImportItemRequest item) {
        return normalizer.normalize(item, LocalEventNormalizer.Mode.IMPORT, NOW);
    }

    private static List<LocalEventFieldErrorCode> codes(NormalizedLocalEvent event, String field) {
        return event.getErrors().stream()
                .filter(error -> field.equals(error.getField()))
                .map(LocalEventFieldError::getCode)
                .toList();
    }
}
