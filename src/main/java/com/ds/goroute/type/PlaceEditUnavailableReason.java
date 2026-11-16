package com.ds.goroute.type;

/** Why the edit-suggestion context answers {@code allowed = false}. */
public enum PlaceEditUnavailableReason {
    /** PLACE_EDIT/ENABLED is off and the caller is not a beta user. */
    DISABLED,
    /** The place is not visible (INACTIVE or not ACTIVE in its lifecycle). */
    PLACE_INACTIVE,
    /** A partner place, or one with a live marketplace listing; a closure report is still allowed. */
    PARTNER_PLACE,
    /** The account is younger than PLACE_EDIT/MIN_ACCOUNT_AGE_HOURS. */
    ACCOUNT_TOO_NEW
}
