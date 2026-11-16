package com.ds.goroute.localevent.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/** GET /v1/api/public/events/{id}. Images are the DONE (re-hosted) ones, in position order. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class LocalEventDetailResponse extends LocalEventDetailFields {

    private List<LocalEventImageResponse> images;

    /**
     * True when {@code summary}, {@code description}, {@code scheduleNote}, {@code priceNote} or
     * {@code lunarDateText} above were replaced by a translation into the reader's language
     * (Accept-Language). The title, venue, address, organiser and performers are never translated.
     */
    private Boolean translated;
    /** The language of the translation used, e.g. {@code en}; null when not translated. */
    private String translationLocale;
    /** {@code AI} or {@code MANUAL}; null when not translated. */
    private String translationSource;
    /** The replaced fields as written; null when not translated. */
    private LocalEventOriginalText original;
}
