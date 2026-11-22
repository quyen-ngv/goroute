package com.ds.goroute.type;

/** Why {@code GET /v1/api/meetups/context} answers {@code canHost = false} for an enabled user. */
public enum MeetupCannotHostReason {
    BIRTH_YEAR_REQUIRED,
    UNDERAGE,
    HOST_LIMIT
}
