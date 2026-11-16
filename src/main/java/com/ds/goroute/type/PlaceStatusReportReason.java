package com.ds.goroute.type;

/** What a BUSINESS_STATUS change reports (plan §3.5). */
public enum PlaceStatusReportReason {
    /** {@code places.status = 'Permanently closed'}; the place stays ACTIVE with a label. */
    PERMANENTLY_CLOSED,
    /** {@code places.status = 'Temporarily closed'}. */
    TEMPORARILY_CLOSED,
    /** Clears a closed status; only accepted while the place carries one. */
    REOPENED,
    /** The place does not exist: it is hidden (INACTIVE). */
    NOT_EXIST,
    /** Same place as {@code duplicateOfPlaceId}: hidden and pointed at the original. */
    DUPLICATE,
    /** Moved away; nothing is applied by itself, the operator corrects address and pin. */
    MOVED
}
