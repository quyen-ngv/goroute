package com.ds.goroute.type;

/** Lifecycle of a guest-initiated change request; it never changes the booking status itself. */
public enum BookingChangeRequestStatus {
    REQUESTED, ACCEPTED, DECLINED, WITHDRAWN, EXPIRED;

    public boolean isOpen() { return this == REQUESTED; }
}
