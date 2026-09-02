package com.ds.goroute.type;

/**
 * Why a booking became billable. Anything not listed here is never on a statement — a cancelled
 * booking without a penalty, an expired hold and a no-show the guest was not charged for all earn
 * the platform nothing.
 */
public enum PartnerStatementLineReason {
    /** Stay/visit happened: gross is the booking total. */
    COMPLETED,
    /** Guest never showed and the partner charged them: gross is the booking total. */
    NO_SHOW_CHARGED,
    /** Cancellation that carried a penalty: gross is the penalty recorded on the cancellation. */
    CANCELLATION_FEE
}
