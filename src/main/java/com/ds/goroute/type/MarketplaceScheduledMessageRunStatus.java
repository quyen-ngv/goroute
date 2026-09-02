package com.ds.goroute.type;

/**
 * Outcome of one (rule, booking) delivery attempt.
 *
 * <p>A run row exists for every outcome and is the duplicate guard, so all three values are
 * terminal for that pair: {@code SKIPPED} and {@code FAILED} are not retried on the next tick.
 * Operators diagnose them from {@code detail} and delete the row to force a retry.
 */
public enum MarketplaceScheduledMessageRunStatus {
    SENT,
    SKIPPED,
    FAILED
}
