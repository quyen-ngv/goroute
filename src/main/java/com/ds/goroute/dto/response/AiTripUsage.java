package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Whether an AI trip generation may start, and why not.
 *
 * <p>A generation needs AI credits up front and a trip-creation slot at the end, when the
 * itinerary is saved as a real trip. Reporting only the first is what let somebody start a
 * generation they could never finish. The client needs both answers before anything is spent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTripUsage {
    private String tier;

    /** True only when both credits and a trip-creation slot are there. */
    private Boolean eligible;

    /** The balance covers AI_CREDIT.MIN_BALANCE_AI_TRIP (always true for beta users). */
    private Boolean creditAvailable;

    /** Trip-creation slots alone: a free slot left, or an unlocked one not yet used. */
    private Boolean tripQuotaAvailable;

    private Integer freeTripQuotaUsed;
    private Integer freeTripQuota;
    private Integer unlockedTripSlots;

    /** When the subscription lapses, so the client can say what a downgrade would cost. */
    private java.time.LocalDateTime tierExpiresAt;
}
