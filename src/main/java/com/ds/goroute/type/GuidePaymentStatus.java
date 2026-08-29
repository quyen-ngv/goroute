package com.ds.goroute.type;

public enum GuidePaymentStatus {
    NONE,
    /** Money is held; neither side has it. */
    HELD,
    CAPTURED,
    REFUNDED,
    /** Paid out to the guide. */
    RELEASED
}
