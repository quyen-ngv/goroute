package com.ds.goroute.type;

/**
 * When a scheduled guest message becomes due.
 *
 * <p>Each value names a moment on the booking; {@code offsetHours} on the rule shifts it. The
 * direction of the shift is part of the trigger, not of the number, so a partner never has to
 * think in negative hours.
 */
public enum MarketplaceScheduledMessageTrigger {
    /** The moment the partner confirmed the booking, plus the offset. */
    ON_BOOKING_CONFIRMED(false),
    /** Check-in date at the property's check-in time, minus the offset. */
    BEFORE_CHECK_IN(true),
    /** Check-out date at the property's check-out time, plus the offset. */
    AFTER_CHECK_OUT(false),
    /** Slot start in the slot's timezone, minus the offset. */
    BEFORE_ACTIVITY(true);

    private final boolean beforeTheMoment;

    MarketplaceScheduledMessageTrigger(boolean beforeTheMoment) { this.beforeTheMoment = beforeTheMoment; }

    /** True when {@code offsetHours} is subtracted from the reference moment rather than added. */
    public boolean isBeforeTheMoment() { return beforeTheMoment; }

    /** Triggers that can only ever match a hotel booking. */
    public boolean appliesToHotels() { return this != BEFORE_ACTIVITY; }

    /** Triggers that can only ever match an activity order. */
    public boolean appliesToActivities() {
        return this == BEFORE_ACTIVITY || this == ON_BOOKING_CONFIRMED;
    }
}
