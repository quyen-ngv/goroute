package com.ds.goroute.type;

/** Why an operator did not apply a change or a whole suggestion. */
public enum PlaceEditRejectReason {
    INCORRECT,
    NOT_VERIFIABLE,
    ALREADY_UP_TO_DATE,
    SPAM,
    OTHER
}
