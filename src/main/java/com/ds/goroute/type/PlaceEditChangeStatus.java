package com.ds.goroute.type;

/** Lifecycle of one field change inside a suggestion. */
public enum PlaceEditChangeStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** Another person's change to the same place and field was approved first. */
    SUPERSEDED,
    CANCELLED
}
