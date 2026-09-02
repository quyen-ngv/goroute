package com.ds.goroute.type;

/**
 * Lifecycle of a monthly partner statement.
 *
 * <p>{@code OPEN} is an accrual that has not been sent yet, {@code ISSUED} is the figure the
 * partner was told about, {@code DISPUTED} means at least one line is contested and
 * {@code SETTLED} closes the period. Settlement is bookkeeping only: no money moves through the
 * platform, there is no payment gateway.
 */
public enum PartnerStatementStatus {
    OPEN,
    ISSUED,
    DISPUTED,
    SETTLED;

    /** A settled period is frozen: regenerating or disputing it would rewrite an agreed figure. */
    public boolean isClosed() { return this == SETTLED; }
}
