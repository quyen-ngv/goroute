package com.ds.goroute.localevent.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * PUT /v1/api/admin/events/{eventId}/translations/{locale}: the event's free text in one
 * language as an admin wrote it. A blank field means "show the original for this field".
 */
@Data
public class LocalEventTranslationRequest {

    @Size(max = 300)
    private String summary;

    @Size(max = 16000)
    private String description;

    @Size(max = 300)
    private String scheduleNote;

    @Size(max = 300)
    private String priceNote;

    @Size(max = 100)
    private String lunarDateText;
}
