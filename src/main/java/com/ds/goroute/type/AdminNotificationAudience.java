package com.ds.goroute.type;

/**
 * Who an admin announcement goes to.
 *
 * <p>Every segment is resolved as a query over {@code users} at send time, never stored as a
 * membership list: an operator who previews "trip starting within 3 days" on Monday and sends
 * on Tuesday must reach Tuesday's travellers, not Monday's.
 */
public enum AdminNotificationAudience {

    /** Every account that has not been soft deleted. */
    ALL_USERS,

    /** Exactly the accounts the operator picked by hand. */
    SPECIFIC_USERS,

    /** Registered within the last {@code withinDays} days — the onboarding window. */
    NEW_USERS,

    /** Has a trip whose date range covers today. */
    ACTIVE_TRIP,

    /** Has a trip starting between today and {@code withinDays} days from now. */
    UPCOMING_TRIP,

    /** Has a trip that ended within the last {@code withinDays} days. */
    RECENT_TRIP_ENDED,

    /** Has never created or joined a trip. */
    NO_TRIP,

    /** Has not signed in for at least {@code withinDays} days, or has never signed in. */
    INACTIVE_USERS;

    /** True when the segment is meaningless without a day window. */
    public boolean requiresWithinDays() {
        return this == NEW_USERS || this == UPCOMING_TRIP || this == RECENT_TRIP_ENDED || this == INACTIVE_USERS;
    }
}
