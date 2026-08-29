package com.ds.goroute.type;

/**
 * Entries in the guide money ledger.
 *
 * <p>Real money, kept in its own ledger and never mixed with the point wallet. An
 * adjustment is a new entry, never an edit: financial history that can be rewritten cannot
 * be reconciled or defended.
 */
public enum GuidePayoutEntryType {
    HOLD,
    CAPTURE,
    PLATFORM_FEE,
    PAYOUT,
    REFUND,
    ADJUSTMENT
}
