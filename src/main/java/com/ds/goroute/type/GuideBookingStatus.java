package com.ds.goroute.type;

import java.util.Set;

/** The life of a guide booking (GUIDE-04). */
public enum GuideBookingStatus {
    REQUESTED,
    ACCEPTED,
    DECLINED,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    DISPUTED,
    /** The guide did not answer in time. */
    EXPIRED;

    private static final Set<GuideBookingStatus> HOLDS_CAPACITY =
            Set.of(ACCEPTED, CONFIRMED, COMPLETED);

    /** Whether a booking in this state occupies one of the guide's slots for the day. */
    public boolean holdsCapacity() {
        return HOLDS_CAPACITY.contains(this);
    }

    public boolean canTransitionTo(GuideBookingStatus next) {
        return switch (this) {
            case REQUESTED -> next == ACCEPTED || next == DECLINED || next == EXPIRED || next == CANCELLED;
            case ACCEPTED -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == COMPLETED || next == CANCELLED || next == DISPUTED;
            case COMPLETED -> next == DISPUTED;
            case DISPUTED -> next == COMPLETED || next == CANCELLED;
            case DECLINED, CANCELLED, EXPIRED -> false;
        };
    }
}
