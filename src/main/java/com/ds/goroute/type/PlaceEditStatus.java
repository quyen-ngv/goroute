package com.ds.goroute.type;

/** Lifecycle of a place edit suggestion. Only PENDING moves; every other value is final. */
public enum PlaceEditStatus {
    PENDING,
    /** Every change that was decided on was applied. */
    APPROVED,
    /** Some changes applied, some rejected. */
    PARTIALLY_APPROVED,
    /** Nothing applied; {@code reject_reason} says why. */
    REJECTED,
    /** Withdrawn by its submitter while still pending. */
    CANCELLED
}
