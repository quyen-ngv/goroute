package com.ds.goroute.type;

/** Fields whose daily override should fall back to the rate-plan default. */
public enum RatePlanCalendarClearField {
    PRICE,
    STOP_SELL,
    MIN_STAY,
    MAX_STAY,
    CLOSED_TO_ARRIVAL,
    CLOSED_TO_DEPARTURE,
    MIN_ADVANCE_DAYS,
    MAX_ADVANCE_DAYS
}
