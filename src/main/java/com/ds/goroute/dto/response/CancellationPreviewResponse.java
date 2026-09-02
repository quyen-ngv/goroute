package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What happens if the guest cancels right now. Shown before the guest confirms a cancellation so the
 * decision is informed, exactly like the policy box on Booking.com / Klook.
 */
@Value
@Builder
public class CancellationPreviewResponse {
    /** False once the stay/visit has started or the booking is already terminal. */
    boolean cancellable;
    /** Why it is not cancellable (null when cancellable). */
    String blockedReason;
    String policyType;
    boolean free;
    /** Last moment of free cancellation, in the property/slot timezone. Null when never free. */
    LocalDateTime freeUntil;
    BigDecimal penaltyAmount;
    String currency;
    /** Rule that produced the penalty, e.g. FREE_UNTIL_24H, PERCENT_50, NON_REFUNDABLE. */
    String penaltyRule;
    /** Service start used for the evaluation, in the property/slot timezone. */
    LocalDateTime serviceStartsAt;
}
