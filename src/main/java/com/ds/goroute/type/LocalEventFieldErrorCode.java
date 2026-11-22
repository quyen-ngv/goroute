package com.ds.goroute.type;

/** Why one field of an imported (or console-edited) local event was refused. */
public enum LocalEventFieldErrorCode {
    REQUIRED,
    INVALID_FORMAT,
    INVALID_ENUM,
    OUT_OF_RANGE,
    TOO_LONG,
    INVALID_URL,
    OUTSIDE_VIETNAM,
    EVENT_ENDED,
    DUPLICATE_IN_BATCH,
    /**
     * The item was valid but could not be saved (for example a console edit landed at the same
     * moment). Nothing was written for it; sending the same item again is safe.
     */
    INTERNAL_ERROR
}
