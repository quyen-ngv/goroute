package com.ds.goroute.localevent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One show/day of a multi-session event. Times are ISO-8601 with an offset or Z. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalEventSessionRequest {

    private String startAt;
    private String endAt;
    private String label;
    /** A LocalEventStatus code; defaults to SCHEDULED. */
    private String status;
}
