package com.ds.goroute.localevent.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One {@code local_events} row. Times are UTC; JSON columns ({@code tags}, {@code sessions},
 * {@code performers}, {@code rawPayload}) are carried as JSON text and parsed by the services.
 * Enum columns are strings here and converted at the service boundary.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class LocalEvent {

    private UUID id;
    private String title;
    private String titleNorm;
    private String summary;
    private String description;
    private String language;
    private String category;
    private String tags;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean endAtEstimated;
    private String timezone;
    private Boolean allDay;
    private String sessions;
    private String scheduleNote;
    private String lunarDateText;
    private String eventStatus;
    private LocalDateTime previousStartAt;
    private String attendanceMode;
    private String onlineUrl;
    private String venueName;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private UUID placeId;
    private String provinceCode;
    private String wardCode;
    private Boolean isFree;
    private Long priceMin;
    private Long priceMax;
    private String currency;
    private String priceNote;
    private String ticketUrl;
    private String ticketAvailability;
    private String organizerName;
    private String organizerUrl;
    private String performers;
    private Integer minAge;
    private String coverUrl;
    private String publishStatus;
    private LocalDateTime publishedAt;
    private String source;
    private String externalId;
    private String sourceUrl;
    private LocalDateTime sourceUpdatedAt;
    private String contentHash;
    private BigDecimal extractionConfidence;
    private String extractedBy;
    private String rawPayload;
    private UUID duplicateOfEventId;
    private Boolean manuallyEdited;
    private UUID createdBy;
    private UUID updatedBy;
    private Boolean isDeleted;
    private Long dataVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Not a column: kilometres from the search centre, filled by the public radius query. */
    private Double distanceKm;
    /** Not a column: {@code jsonb_array_length(sessions)}, filled by the public list query. */
    private Integer sessionCount;
}
