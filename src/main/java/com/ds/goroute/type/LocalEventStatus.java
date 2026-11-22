package com.ds.goroute.type;

/**
 * Whether a local event still happens as announced (schema.org EventStatusType). Independent of
 * {@link LocalEventPublishStatus}: a cancelled event stays published so people who saw it learn
 * it is off. MOVED_ONLINE events are left out of the public map.
 */
public enum LocalEventStatus {
    SCHEDULED,
    RESCHEDULED,
    POSTPONED,
    CANCELLED,
    MOVED_ONLINE
}
