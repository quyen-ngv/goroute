package com.ds.goroute.localevent.service;

import com.ds.goroute.entity.Ward;
import com.ds.goroute.localevent.dto.LocalEventFieldError;
import com.ds.goroute.localevent.dto.LocalEventImageRequest;
import com.ds.goroute.localevent.dto.LocalEventImportItemRequest;
import com.ds.goroute.localevent.dto.LocalEventSessionRequest;
import com.ds.goroute.localevent.dto.LocalEventUpsertRequest;
import com.ds.goroute.localevent.dto.LocalEventVenueRequest;
import com.ds.goroute.service.GeoService;
import com.ds.goroute.type.LocalEventAttendanceMode;
import com.ds.goroute.type.LocalEventCategory;
import com.ds.goroute.type.LocalEventFieldErrorCode;
import com.ds.goroute.type.LocalEventPublishStatus;
import com.ds.goroute.type.LocalEventStatus;
import com.ds.goroute.type.LocalEventTicketAvailability;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Currency;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.ds.goroute.type.LocalEventFieldErrorCode.EVENT_ENDED;
import static com.ds.goroute.type.LocalEventFieldErrorCode.INVALID_ENUM;
import static com.ds.goroute.type.LocalEventFieldErrorCode.INVALID_FORMAT;
import static com.ds.goroute.type.LocalEventFieldErrorCode.INVALID_URL;
import static com.ds.goroute.type.LocalEventFieldErrorCode.OUTSIDE_VIETNAM;
import static com.ds.goroute.type.LocalEventFieldErrorCode.OUT_OF_RANGE;
import static com.ds.goroute.type.LocalEventFieldErrorCode.REQUIRED;
import static com.ds.goroute.type.LocalEventFieldErrorCode.TOO_LONG;

/**
 * Validates and converts one local event (console create/edit or one import item) against the
 * import contract (SPEC_V2 §1.5, documented in .docs/designs/apis/25_EVENTS_IMPORT_API.md).
 * Every broken rule is collected as a field error instead of stopping at the first one, so a
 * crawler sees all of an item's problems in one round trip.
 */
@Component
@RequiredArgsConstructor
public class LocalEventNormalizer {

    /** What is being normalised, which decides the rules that apply. */
    public enum Mode {
        /** POST /admin/events/import: source fields required, ended events refused. */
        IMPORT,
        /** POST /admin/events: ended events refused. */
        ADMIN_CREATE,
        /** PUT /admin/events/{id}: an admin may still correct an event that has ended. */
        ADMIN_UPDATE
    }

    static final int TITLE_MAX = 200;
    static final int SUMMARY_MAX = 300;
    static final int DESCRIPTION_MAX = 16_000;
    static final int LANGUAGE_MAX = 10;
    static final int TAGS_MAX = 20;
    static final int TAG_MAX = 50;
    static final int SESSIONS_MAX = 50;
    static final int SESSION_LABEL_MAX = 100;
    static final int NOTE_MAX = 300;
    static final int LUNAR_MAX = 100;
    static final int VENUE_NAME_MAX = 200;
    static final int ADDRESS_MAX = 500;
    static final int ORGANIZER_NAME_MAX = 200;
    static final int PERFORMERS_MAX = 30;
    static final int PERFORMER_MAX = 100;
    static final int IMAGES_MAX = 10;
    static final int CREDIT_MAX = 200;
    static final int URL_MAX = 1000;
    static final int EXTERNAL_ID_MAX = 255;
    static final int EXTRACTED_BY_MAX = 100;
    static final int RAW_PAYLOAD_MAX_BYTES = 64 * 1024;
    static final int MIN_AGE_MAX = 99;
    /** An event without an end time is assumed to last this long. */
    static final Duration DEFAULT_DURATION = Duration.ofHours(3);

    /** Vietnam's bounding box (with the islands); a first, cheap check before the ward lookup. */
    static final BigDecimal VN_MIN_LAT = new BigDecimal("8.1");
    static final BigDecimal VN_MAX_LAT = new BigDecimal("23.4");
    static final BigDecimal VN_MIN_LNG = new BigDecimal("102.1");
    static final BigDecimal VN_MAX_LNG = new BigDecimal("109.5");

    static final Pattern SOURCE = Pattern.compile("^[a-z0-9][a-z0-9._-]{1,49}$");
    static final Pattern LANGUAGE = Pattern.compile("^[A-Za-z]{2,3}(-[A-Za-z0-9]{1,8})*$");

    private final GeoService geoService;
    private final ObjectMapper objectMapper;

    public NormalizedLocalEvent normalize(LocalEventUpsertRequest request, Mode mode, LocalDateTime nowUtc) {
        Errors errors = new Errors();
        NormalizedLocalEvent.NormalizedLocalEventBuilder out = NormalizedLocalEvent.builder();

        if (mode == Mode.IMPORT) {
            readSource(request, errors, out);
        }

        String title = requiredText("title", LocalEventText.singleLine(request.getTitle()), TITLE_MAX, errors);
        out.title(title).titleNorm(LocalEventText.normalizeTitle(title));
        out.summary(optionalText("summary", LocalEventText.singleLine(request.getSummary()), SUMMARY_MAX, errors));
        out.description(optionalText("description", LocalEventText.multiLine(request.getDescription()),
                DESCRIPTION_MAX, errors));
        out.language(readLanguage(request.getLanguage(), errors));
        out.category(requiredEnum("category", request.getCategory(), LocalEventCategory.class, errors));
        out.tags(readTextList("tags", request.getTags(), TAGS_MAX, TAG_MAX, errors));
        out.scheduleNote(optionalText("scheduleNote", LocalEventText.singleLine(request.getScheduleNote()),
                NOTE_MAX, errors));
        out.lunarDateText(optionalText("lunarDateText", LocalEventText.singleLine(request.getLunarDateText()),
                LUNAR_MAX, errors));

        String requestedStatus = optionalEnum("eventStatus", request.getEventStatus(), LocalEventStatus.class, errors);
        String eventStatus = requestedStatus == null ? LocalEventStatus.SCHEDULED.name() : requestedStatus;
        out.eventStatus(eventStatus);
        String attendanceMode = optionalEnum("attendanceMode", request.getAttendanceMode(),
                LocalEventAttendanceMode.class, errors);
        out.attendanceMode(attendanceMode == null ? LocalEventAttendanceMode.OFFLINE.name() : attendanceMode);
        out.onlineUrl(optionalUrl("onlineUrl", request.getOnlineUrl(), errors));

        readTimes(request, mode, nowUtc, errors, out);
        readPreviousStart(request.getPreviousStartAt(), eventStatus, errors, out);
        readVenue(request.getVenue(), errors, out);
        readPrices(request, errors, out);

        String availability = optionalEnum("ticketAvailability", request.getTicketAvailability(),
                LocalEventTicketAvailability.class, errors);
        out.ticketAvailability(availability == null ? LocalEventTicketAvailability.UNKNOWN.name() : availability);
        out.ticketUrl(optionalUrl("ticketUrl", request.getTicketUrl(), errors));
        out.priceNote(optionalText("priceNote", LocalEventText.singleLine(request.getPriceNote()), NOTE_MAX, errors));

        if (request.getOrganizer() != null) {
            out.organizerName(optionalText("organizer.name",
                    LocalEventText.singleLine(request.getOrganizer().getName()), ORGANIZER_NAME_MAX, errors));
            out.organizerUrl(optionalUrl("organizer.url", request.getOrganizer().getUrl(), errors));
        }
        out.performers(readTextList("performers", request.getPerformers(), PERFORMERS_MAX, PERFORMER_MAX, errors));
        if (request.getMinAge() != null && (request.getMinAge() < 0 || request.getMinAge() > MIN_AGE_MAX)) {
            errors.add("minAge", OUT_OF_RANGE, "minAge must be between 0 and " + MIN_AGE_MAX);
        } else {
            out.minAge(request.getMinAge());
        }
        out.images(readImages(request.getImages(), errors));
        out.sourceUrl(optionalUrl("sourceUrl", request.getSourceUrl(), errors));

        String publishStatus = optionalEnum("publishStatus", request.getPublishStatus(),
                LocalEventPublishStatus.class, errors);
        if (LocalEventPublishStatus.HIDDEN.name().equals(publishStatus)) {
            errors.add("publishStatus", INVALID_ENUM, "publishStatus must be DRAFT or PUBLISHED");
            publishStatus = null;
        }
        out.publishStatus(publishStatus == null ? LocalEventPublishStatus.DRAFT.name() : publishStatus);

        NormalizedLocalEvent normalized = out.errors(errors.list).build();
        if (normalized.isValid()) {
            normalized.setContentHash(contentHash(normalized));
        }
        return normalized;
    }

    // --- source -------------------------------------------------------------------------

    private void readSource(LocalEventUpsertRequest request, Errors errors,
                            NormalizedLocalEvent.NormalizedLocalEventBuilder out) {
        LocalEventImportItemRequest item = request instanceof LocalEventImportItemRequest importItem
                ? importItem
                : new LocalEventImportItemRequest();
        String source = item.getSource() == null ? null : item.getSource().trim();
        if (source == null || source.isEmpty()) {
            errors.add("source", REQUIRED, "source is required");
        } else if (!SOURCE.matcher(source).matches()) {
            errors.add("source", INVALID_FORMAT,
                    "source must match ^[a-z0-9][a-z0-9._-]{1,49}$ (lower-case, e.g. ticketbox.vn)");
        } else {
            out.source(source);
        }
        String externalId = item.getExternalId() == null ? null : item.getExternalId().trim();
        if (externalId == null || externalId.isEmpty()) {
            errors.add("externalId", REQUIRED, "externalId is required");
        } else if (externalId.length() > EXTERNAL_ID_MAX) {
            errors.add("externalId", TOO_LONG, "externalId must be at most " + EXTERNAL_ID_MAX + " characters");
        } else {
            out.externalId(externalId);
        }
        if (item.getSourceUpdatedAt() != null && !item.getSourceUpdatedAt().isBlank()) {
            Optional<Instant> updated = LocalEventTimes.parseInstant(item.getSourceUpdatedAt());
            if (updated.isEmpty()) {
                errors.add("sourceUpdatedAt", INVALID_FORMAT, "sourceUpdatedAt must be ISO-8601 with an offset or Z");
            } else {
                out.sourceUpdatedAt(LocalEventTimes.toUtc(updated.get()));
            }
        }
        BigDecimal confidence = item.getExtractionConfidence();
        if (confidence != null && (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0)) {
            errors.add("extractionConfidence", OUT_OF_RANGE, "extractionConfidence must be between 0 and 1");
        } else if (confidence != null) {
            out.extractionConfidence(confidence.setScale(3, java.math.RoundingMode.HALF_UP));
        }
        out.extractedBy(optionalText("extractedBy", LocalEventText.singleLine(item.getExtractedBy()),
                EXTRACTED_BY_MAX, errors));
        JsonNode raw = item.getRawPayload();
        if (raw != null && !raw.isNull()) {
            if (!raw.isObject()) {
                errors.add("rawPayload", INVALID_FORMAT, "rawPayload must be a JSON object");
            } else {
                String text = raw.toString();
                if (text.getBytes(StandardCharsets.UTF_8).length > RAW_PAYLOAD_MAX_BYTES) {
                    errors.add("rawPayload", TOO_LONG, "rawPayload must be at most 64 KB once serialised");
                } else {
                    out.rawPayload(text);
                }
            }
        }
        out.overwriteManualEdits(Boolean.TRUE.equals(item.getOverwriteManualEdits()));
    }

    // --- time ---------------------------------------------------------------------------

    private void readTimes(LocalEventUpsertRequest request, Mode mode, LocalDateTime nowUtc, Errors errors,
                           NormalizedLocalEvent.NormalizedLocalEventBuilder out) {
        ZoneId zone = LocalEventTimes.VIETNAM;
        if (request.getTimezone() != null && !request.getTimezone().isBlank()) {
            try {
                zone = ZoneId.of(request.getTimezone().trim());
            } catch (DateTimeException exception) {
                errors.add("timezone", INVALID_FORMAT, "timezone must be an IANA zone such as Asia/Ho_Chi_Minh");
            }
        }
        out.timezone(zone.getId());
        boolean allDay = Boolean.TRUE.equals(request.getAllDay());
        out.allDay(allDay);

        Span top = readSpan("startAt", request.getStartAt(), "endAt", request.getEndAt(), allDay, zone, errors, true);
        List<NormalizedLocalEvent.Session> sessions = readSessions(request.getSessions(), allDay, zone, errors);
        out.sessions(sessions);
        if (top == null) {
            return;
        }

        LocalDateTime start = top.start();
        LocalDateTime end = top.end();
        boolean estimated = top.estimated();
        if (!sessions.isEmpty()) {
            // The span of a multi-session event is its first session's start to its last one's end.
            start = sessions.stream().map(NormalizedLocalEvent.Session::startAt).min(Comparator.naturalOrder()).orElse(start);
            end = sessions.stream().map(NormalizedLocalEvent.Session::endAt).max(Comparator.naturalOrder()).orElse(end);
            estimated = false;
        }
        if (end.isBefore(start)) {
            errors.add("endAt", OUT_OF_RANGE, "endAt must not be before startAt");
            return;
        }
        if (mode != Mode.ADMIN_UPDATE && !end.isAfter(nowUtc)) {
            errors.add("endAt", EVENT_ENDED, "The event has already ended");
        }
        out.startAt(start).endAt(end).endAtEstimated(estimated);
    }

    private record Span(LocalDateTime start, LocalDateTime end, boolean estimated) {
    }

    /**
     * Reads a start/end pair. All-day: a date (YYYY-MM-DD) or a date-time is accepted and the span
     * becomes local midnight of the first day to local 23:59:59 of the last. Otherwise both must
     * carry an offset; a missing end means start + 3 h, marked estimated.
     */
    private Span readSpan(String startField, String startValue, String endField, String endValue, boolean allDay,
                          ZoneId zone, Errors errors, boolean startRequired) {
        if (startValue == null || startValue.isBlank()) {
            if (startRequired) {
                errors.add(startField, REQUIRED, startField + " is required");
            }
            return null;
        }
        boolean hasEnd = endValue != null && !endValue.isBlank();
        if (allDay) {
            Optional<LocalDate> startDate = readLocalDate(startField, startValue, zone, errors);
            Optional<LocalDate> endDate = hasEnd ? readLocalDate(endField, endValue, zone, errors) : startDate;
            if (startDate.isEmpty() || endDate.isEmpty()) {
                return null;
            }
            if (endDate.get().isBefore(startDate.get())) {
                errors.add(endField, OUT_OF_RANGE, endField + " must not be before " + startField);
                return null;
            }
            return new Span(LocalEventTimes.startOfDayUtc(startDate.get(), zone),
                    LocalEventTimes.endOfDayUtc(endDate.get(), zone), !hasEnd);
        }
        Optional<Instant> start = LocalEventTimes.parseInstant(startValue);
        if (start.isEmpty()) {
            errors.add(startField, INVALID_FORMAT, startField + " must be ISO-8601 with an offset or Z, e.g. 2026-11-07T19:30:00+07:00");
            return null;
        }
        Instant end;
        if (hasEnd) {
            Optional<Instant> parsedEnd = LocalEventTimes.parseInstant(endValue);
            if (parsedEnd.isEmpty()) {
                errors.add(endField, INVALID_FORMAT, endField + " must be ISO-8601 with an offset or Z");
                return null;
            }
            end = parsedEnd.get();
            if (end.isBefore(start.get())) {
                errors.add(endField, OUT_OF_RANGE, endField + " must not be before " + startField);
                return null;
            }
        } else {
            end = start.get().plus(DEFAULT_DURATION);
        }
        return new Span(LocalEventTimes.toUtc(start.get()), LocalEventTimes.toUtc(end), !hasEnd);
    }

    private Optional<LocalDate> readLocalDate(String field, String value, ZoneId zone, Errors errors) {
        Optional<LocalDate> date = LocalEventTimes.parseDate(value);
        if (date.isPresent()) {
            return date;
        }
        Optional<Instant> instant = LocalEventTimes.parseInstant(value);
        if (instant.isPresent()) {
            return Optional.of(LocalEventTimes.localDate(instant.get(), zone));
        }
        errors.add(field, INVALID_FORMAT, field + " must be a date (YYYY-MM-DD) or ISO-8601 with an offset");
        return Optional.empty();
    }

    private List<NormalizedLocalEvent.Session> readSessions(List<LocalEventSessionRequest> requested, boolean allDay,
                                                            ZoneId zone, Errors errors) {
        List<NormalizedLocalEvent.Session> sessions = new ArrayList<>();
        if (requested == null || requested.isEmpty()) {
            return sessions;
        }
        if (requested.size() > SESSIONS_MAX) {
            errors.add("sessions", OUT_OF_RANGE, "At most " + SESSIONS_MAX + " sessions");
            return sessions;
        }
        for (int index = 0; index < requested.size(); index++) {
            LocalEventSessionRequest session = requested.get(index);
            String prefix = "sessions[" + index + "].";
            if (session == null) {
                errors.add("sessions[" + index + "]", REQUIRED, "A session must be an object");
                continue;
            }
            Span span = readSpan(prefix + "startAt", session.getStartAt(), prefix + "endAt", session.getEndAt(),
                    allDay, zone, errors, true);
            String label = optionalText(prefix + "label", LocalEventText.singleLine(session.getLabel()),
                    SESSION_LABEL_MAX, errors);
            String status = optionalEnum(prefix + "status", session.getStatus(), LocalEventStatus.class, errors);
            if (span != null) {
                sessions.add(new NormalizedLocalEvent.Session(span.start(), span.end(), label,
                        status == null ? LocalEventStatus.SCHEDULED.name() : status));
            }
        }
        sessions.sort(Comparator.comparing(NormalizedLocalEvent.Session::startAt));
        return sessions;
    }

    private void readPreviousStart(String value, String eventStatus, Errors errors,
                                   NormalizedLocalEvent.NormalizedLocalEventBuilder out) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!LocalEventStatus.RESCHEDULED.name().equals(eventStatus)) {
            errors.add("previousStartAt", OUT_OF_RANGE, "previousStartAt is only allowed when eventStatus is RESCHEDULED");
            return;
        }
        Optional<Instant> previous = LocalEventTimes.parseInstant(value);
        if (previous.isEmpty()) {
            errors.add("previousStartAt", INVALID_FORMAT, "previousStartAt must be ISO-8601 with an offset or Z");
            return;
        }
        out.previousStartAt(LocalEventTimes.toUtc(previous.get()));
    }

    // --- place --------------------------------------------------------------------------

    private void readVenue(LocalEventVenueRequest venue, Errors errors,
                           NormalizedLocalEvent.NormalizedLocalEventBuilder out) {
        if (venue == null) {
            errors.add("venue", REQUIRED, "venue is required");
            return;
        }
        out.venueName(requiredText("venue.name", LocalEventText.singleLine(venue.getName()), VENUE_NAME_MAX, errors));
        out.address(optionalText("venue.address", LocalEventText.singleLine(venue.getAddress()), ADDRESS_MAX, errors));
        if (venue.getPlaceId() != null && !venue.getPlaceId().isBlank()) {
            try {
                out.placeId(UUID.fromString(venue.getPlaceId().trim()));
            } catch (IllegalArgumentException exception) {
                errors.add("venue.placeId", INVALID_FORMAT, "venue.placeId must be a UUID");
            }
        }
        BigDecimal latitude = venue.getLatitude();
        BigDecimal longitude = venue.getLongitude();
        if (latitude == null) {
            errors.add("venue.latitude", REQUIRED, "venue.latitude is required");
        }
        if (longitude == null) {
            errors.add("venue.longitude", REQUIRED, "venue.longitude is required");
        }
        if (latitude == null || longitude == null) {
            return;
        }
        if (latitude.compareTo(VN_MIN_LAT) < 0 || latitude.compareTo(VN_MAX_LAT) > 0
                || longitude.compareTo(VN_MIN_LNG) < 0 || longitude.compareTo(VN_MAX_LNG) > 0) {
            errors.add("venue", OUTSIDE_VIETNAM, "The venue coordinates are outside Vietnam");
            return;
        }
        BigDecimal lat = latitude.setScale(7, java.math.RoundingMode.HALF_UP);
        BigDecimal lng = longitude.setScale(7, java.math.RoundingMode.HALF_UP);
        Optional<Ward> ward = geoService.resolveWard(lat, lng);
        if (ward.isEmpty()) {
            errors.add("venue", OUTSIDE_VIETNAM, "The venue coordinates are not inside any Vietnamese ward");
            return;
        }
        out.latitude(lat).longitude(lng)
                .wardCode(ward.get().getCode())
                .provinceCode(ward.get().getProvinceCode());
    }

    // --- prices -------------------------------------------------------------------------

    private void readPrices(LocalEventUpsertRequest request, Errors errors,
                            NormalizedLocalEvent.NormalizedLocalEventBuilder out) {
        Long min = request.getPriceMin();
        Long max = request.getPriceMax();
        boolean valid = true;
        if (min != null && min < 0) {
            errors.add("priceMin", OUT_OF_RANGE, "priceMin must be 0 or more");
            valid = false;
        }
        if (max != null && max < 0) {
            errors.add("priceMax", OUT_OF_RANGE, "priceMax must be 0 or more");
            valid = false;
        }
        if (valid && min != null && max != null && min > max) {
            errors.add("priceMax", OUT_OF_RANGE, "priceMin must not exceed priceMax");
            valid = false;
        }
        if (Boolean.TRUE.equals(request.getIsFree())
                && ((min != null && min != 0) || (max != null && max != 0))) {
            errors.add("isFree", OUT_OF_RANGE, "A free event has no price (priceMin/priceMax 0 or empty)");
            valid = false;
        }
        if (valid) {
            out.isFree(request.getIsFree()).priceMin(min).priceMax(max);
        }
        String currency = request.getCurrency() == null || request.getCurrency().isBlank()
                ? "VND"
                : request.getCurrency().trim().toUpperCase(Locale.ROOT);
        try {
            if (currency.length() != 3) {
                throw new IllegalArgumentException();
            }
            Currency.getInstance(currency);
            out.currency(currency);
        } catch (IllegalArgumentException exception) {
            errors.add("currency", INVALID_FORMAT, "currency must be an ISO-4217 code such as VND");
        }
    }

    // --- lists --------------------------------------------------------------------------

    private List<NormalizedLocalEvent.Image> readImages(List<LocalEventImageRequest> requested, Errors errors) {
        List<NormalizedLocalEvent.Image> images = new ArrayList<>();
        if (requested == null || requested.isEmpty()) {
            return images;
        }
        if (requested.size() > IMAGES_MAX) {
            errors.add("images", OUT_OF_RANGE, "At most " + IMAGES_MAX + " images");
            return images;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (int index = 0; index < requested.size(); index++) {
            LocalEventImageRequest image = requested.get(index);
            String prefix = "images[" + index + "].";
            if (image == null || image.getUrl() == null || image.getUrl().isBlank()) {
                errors.add(prefix + "url", REQUIRED, "An image needs a url");
                continue;
            }
            String url = optionalUrl(prefix + "url", image.getUrl(), errors);
            String credit = optionalText(prefix + "credit", LocalEventText.singleLine(image.getCredit()), CREDIT_MAX, errors);
            if (url != null && seen.add(url)) {
                images.add(new NormalizedLocalEvent.Image(url, credit));
            }
        }
        return images;
    }

    private List<String> readTextList(String field, List<String> values, int maxCount, int maxLength, Errors errors) {
        List<String> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        if (values.size() > maxCount) {
            errors.add(field, OUT_OF_RANGE, "At most " + maxCount + " " + field);
            return result;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (int index = 0; index < values.size(); index++) {
            String value = LocalEventText.singleLine(values.get(index));
            if (value == null) {
                continue;
            }
            if (value.length() > maxLength) {
                errors.add(field + "[" + index + "]", TOO_LONG, "Each of " + field + " must be at most " + maxLength + " characters");
                continue;
            }
            if (seen.add(value.toLowerCase(Locale.ROOT))) {
                result.add(value);
            }
        }
        return result;
    }

    // --- primitives ---------------------------------------------------------------------

    private String readLanguage(String value, Errors errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String language = value.trim();
        if (language.length() > LANGUAGE_MAX) {
            errors.add("language", TOO_LONG, "language must be at most " + LANGUAGE_MAX + " characters");
            return null;
        }
        if (!LANGUAGE.matcher(language).matches()) {
            errors.add("language", INVALID_FORMAT, "language must be a BCP-47 tag such as vi or en");
            return null;
        }
        return Locale.forLanguageTag(language).toLanguageTag();
    }

    private String requiredText(String field, String value, int max, Errors errors) {
        if (value == null) {
            errors.add(field, REQUIRED, field + " is required");
            return null;
        }
        return optionalText(field, value, max, errors);
    }

    private String optionalText(String field, String value, int max, Errors errors) {
        if (value == null) {
            return null;
        }
        if (value.length() > max) {
            errors.add(field, TOO_LONG, field + " must be at most " + max + " characters");
            return null;
        }
        return value;
    }

    private String optionalUrl(String field, String value, Errors errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String url = value.trim();
        if (url.length() > URL_MAX) {
            errors.add(field, TOO_LONG, field + " must be at most " + URL_MAX + " characters");
            return null;
        }
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
                errors.add(field, INVALID_URL, field + " must be an absolute https URL");
                return null;
            }
        } catch (Exception exception) {
            errors.add(field, INVALID_URL, field + " must be an absolute https URL");
            return null;
        }
        return url;
    }

    private <E extends Enum<E>> String requiredEnum(String field, String value, Class<E> type, Errors errors) {
        if (value == null || value.isBlank()) {
            errors.add(field, REQUIRED, field + " is required");
            return null;
        }
        return optionalEnum(field, value, type, errors);
    }

    private <E extends Enum<E>> String optionalEnum(String field, String value, Class<E> type, Errors errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String code = value.trim().toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, code).name();
        } catch (IllegalArgumentException exception) {
            errors.add(field, INVALID_ENUM, field + " must be one of " + Arrays.toString(type.getEnumConstants()));
            return null;
        }
    }

    // --- hash ---------------------------------------------------------------------------

    /**
     * SHA-256 of the normalised content. Source metadata (sourceUpdatedAt, extraction fields, raw
     * payload, overwrite flag) and the create-only publish status are left out, so a re-crawl that
     * only refreshed those answers UNCHANGED.
     */
    String contentHash(NormalizedLocalEvent event) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("title", event.getTitle());
        canonical.put("summary", event.getSummary());
        canonical.put("description", event.getDescription());
        canonical.put("language", event.getLanguage());
        canonical.put("category", event.getCategory());
        canonical.put("tags", event.getTags());
        canonical.put("startAt", String.valueOf(event.getStartAt()));
        canonical.put("endAt", String.valueOf(event.getEndAt()));
        canonical.put("endAtEstimated", event.isEndAtEstimated());
        canonical.put("timezone", event.getTimezone());
        canonical.put("allDay", event.isAllDay());
        canonical.put("sessions", event.getSessions().stream()
                .map(session -> List.of(String.valueOf(session.startAt()), String.valueOf(session.endAt()),
                        String.valueOf(session.label()), String.valueOf(session.status())))
                .toList());
        canonical.put("scheduleNote", event.getScheduleNote());
        canonical.put("lunarDateText", event.getLunarDateText());
        canonical.put("eventStatus", event.getEventStatus());
        canonical.put("previousStartAt", String.valueOf(event.getPreviousStartAt()));
        canonical.put("attendanceMode", event.getAttendanceMode());
        canonical.put("onlineUrl", event.getOnlineUrl());
        canonical.put("venueName", event.getVenueName());
        canonical.put("address", event.getAddress());
        canonical.put("latitude", event.getLatitude() == null ? null : event.getLatitude().toPlainString());
        canonical.put("longitude", event.getLongitude() == null ? null : event.getLongitude().toPlainString());
        canonical.put("placeId", String.valueOf(event.getPlaceId()));
        canonical.put("isFree", event.getIsFree());
        canonical.put("priceMin", event.getPriceMin());
        canonical.put("priceMax", event.getPriceMax());
        canonical.put("currency", event.getCurrency());
        canonical.put("priceNote", event.getPriceNote());
        canonical.put("ticketUrl", event.getTicketUrl());
        canonical.put("ticketAvailability", event.getTicketAvailability());
        canonical.put("organizerName", event.getOrganizerName());
        canonical.put("organizerUrl", event.getOrganizerUrl());
        canonical.put("performers", event.getPerformers());
        canonical.put("minAge", event.getMinAge());
        canonical.put("images", event.getImages().stream()
                .map(image -> List.of(image.url(), String.valueOf(image.credit())))
                .toList());
        canonical.put("sourceUrl", event.getSourceUrl());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not hash event content", exception);
        }
    }

    /** Error collector; a field may report more than one problem. */
    private static final class Errors {
        private final List<LocalEventFieldError> list = new ArrayList<>();

        void add(String field, LocalEventFieldErrorCode code, String message) {
            list.add(LocalEventFieldError.of(field, code, message));
        }
    }
}
