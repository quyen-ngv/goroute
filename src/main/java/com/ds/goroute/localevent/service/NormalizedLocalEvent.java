package com.ds.goroute.localevent.service;

import com.ds.goroute.localevent.dto.LocalEventFieldError;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A validated, converted local event: every time in UTC, every enum a code that passed its
 * enum, every text trimmed and stripped of control characters. Produced by
 * {@link LocalEventNormalizer}; when {@link #getErrors()} is not empty nothing else is reliable.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class NormalizedLocalEvent {

    // --- content (part of the content hash) -----------------------------------------------
    private String title;
    private String titleNorm;
    private String summary;
    private String description;
    private String language;
    private String category;
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private boolean endAtEstimated;
    private String timezone;
    private boolean allDay;
    @Builder.Default
    private List<Session> sessions = new ArrayList<>();
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
    @Builder.Default
    private List<String> performers = new ArrayList<>();
    private Integer minAge;
    @Builder.Default
    private List<Image> images = new ArrayList<>();
    private String sourceUrl;

    // --- applied on create only -----------------------------------------------------------
    private String publishStatus;

    // --- import metadata (not part of the content hash) -----------------------------------
    private String source;
    private String externalId;
    private LocalDateTime sourceUpdatedAt;
    private BigDecimal extractionConfidence;
    private String extractedBy;
    /** JSON text of the crawler's raw payload, at most 64 KB. */
    private String rawPayload;
    private boolean overwriteManualEdits;

    private String contentHash;

    @Builder.Default
    private List<LocalEventFieldError> errors = new ArrayList<>();

    public boolean isValid() {
        return errors == null || errors.isEmpty();
    }

    /** One session of the event, UTC. */
    public record Session(LocalDateTime startAt, LocalDateTime endAt, String label, String status) {
    }

    /** One image in display order; the first is the cover. */
    public record Image(String url, String credit) {
    }
}
