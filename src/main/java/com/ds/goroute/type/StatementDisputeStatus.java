package com.ds.goroute.type;

/**
 * Dispute state of a single statement line. Accepting a dispute zeroes that line's commission and
 * the statement totals are recomputed from the lines; rejecting it keeps the charge and records why.
 */
public enum StatementDisputeStatus {
    NONE,
    OPEN,
    ACCEPTED,
    REJECTED;

    public boolean isResolved() { return this == ACCEPTED || this == REJECTED; }
}
