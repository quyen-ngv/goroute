package com.ds.goroute.localevent.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One {@code local_event_translations} row (V285): an event's free text in one language.
 * {@code translationSource} is {@code AI} or {@code MANUAL}; {@code status} is {@code DONE} or
 * {@code FAILED}; {@code sourceHash} is the event's {@code content_hash} the text was made from.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalEventTranslation {

    private UUID eventId;
    private String locale;
    private String summary;
    private String description;
    private String scheduleNote;
    private String priceNote;
    private String lunarDateText;
    private String translationSource;
    private String status;
    private String sourceHash;
    private Integer attempts;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
