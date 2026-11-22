package com.ds.goroute.localevent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * The content of a local event, as the console creates/edits it (POST/PUT /v1/api/admin/events)
 * and as one import item carries it ({@link LocalEventImportItemRequest} adds the source fields).
 *
 * <p>Times and enum codes are strings on purpose: one malformed item of an import must fail on
 * its own with a field error, not make the whole body unreadable. {@code LocalEventNormalizer}
 * validates and converts every field. Operator text is not run through @ModeratedText (admin and
 * admin-API-key only, like ImportPlaceRequest).
 */
@Data
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocalEventUpsertRequest {

    private String title;
    private String summary;
    private String description;
    private String language;
    private String category;
    private List<String> tags;
    private String startAt;
    private String endAt;
    private String timezone;
    private Boolean allDay;
    private List<LocalEventSessionRequest> sessions;
    private String scheduleNote;
    private String lunarDateText;
    private String eventStatus;
    private String previousStartAt;
    private String attendanceMode;
    private String onlineUrl;
    private LocalEventVenueRequest venue;
    private Boolean isFree;
    private Long priceMin;
    private Long priceMax;
    private String currency;
    private String priceNote;
    private String ticketUrl;
    private String ticketAvailability;
    private LocalEventOrganizerRequest organizer;
    private List<String> performers;
    private Integer minAge;
    private List<LocalEventImageRequest> images;
    /** DRAFT (default) or PUBLISHED; applied when the event is created, ignored afterwards. */
    private String publishStatus;
    private String sourceUrl;
    /** Required by PUT /v1/api/admin/events/{id}; ignored elsewhere. */
    private Long expectedVersion;
}
