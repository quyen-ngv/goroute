package com.ds.goroute.localevent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The event's free text as it was written, sent beside a translated detail so the app can offer
 * "show original". Only the fields a translation can replace.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalEventOriginalText {

    private String summary;
    private String description;
    private String scheduleNote;
    private String priceNote;
    private String lunarDateText;
}
