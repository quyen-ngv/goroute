package com.ds.goroute.localevent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One language of GET /v1/api/admin/events/{eventId}/translations. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalEventTranslationResponse {

    private String locale;
    private String summary;
    private String description;
    private String scheduleNote;
    private String priceNote;
    private String lunarDateText;
    /** {@code AI} or {@code MANUAL}. */
    private String translationSource;
    /** {@code DONE} or {@code FAILED} (a failed AI attempt; retried while attempts remain). */
    private String status;
    /** False when the event's content changed after this text was written. */
    private boolean upToDate;
    private LocalDateTime updatedAt;
}
