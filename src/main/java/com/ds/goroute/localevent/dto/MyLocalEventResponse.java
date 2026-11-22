package com.ds.goroute.localevent.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * One row of GET /v1/api/events/mine: an event the caller saved or is going to, with the
 * caller's own state on it. The app uses it for the Feed's "your events" strip and to offer the
 * event first when the caller checks in on a day it is on.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class MyLocalEventResponse extends LocalEventSummaryResponse {

    private boolean saved;
    private boolean going;
    /** The caller already has a check-in at this event. */
    private boolean checkedIn;
}
