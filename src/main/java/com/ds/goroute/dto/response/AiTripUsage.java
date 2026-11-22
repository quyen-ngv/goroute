package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Whether an AI trip generation may start, and why not.
 *
 * <p>A generation needs credits for the AI and for saving the itinerary as a real trip.
 * Reporting only the first is what let somebody start a generation they could never finish.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTripUsage {
    private String tier;

    /** Same as creditAvailable; kept as the one answer older clients ask. */
    private Boolean eligible;

    /**
     * The balance covers AI_CREDIT.MIN_BALANCE_AI_TRIP + AI_CREDIT.TRIP_CREATION_COST (always true
     * for beta users).
     */
    private Boolean creditAvailable;

    /** Credits saving the result as a trip costs (AI_CREDIT.TRIP_CREATION_COST). */
    private Integer tripCreationCost;

    // LEGACY-STAR-QUOTA: read by app builds up to 21; worked out from credits since V262. Delete
    // once APP.MIN_BUILD_* passes the first credit build.

    /** LEGACY-STAR-QUOTA: the balance covers one trip creation. */
    @Deprecated
    private Boolean tripQuotaAvailable;
    /** LEGACY-STAR-QUOTA: always 0. */
    @Deprecated
    private Integer freeTripQuotaUsed;
    /** LEGACY-STAR-QUOTA: always 0. */
    @Deprecated
    private Integer freeTripQuota;
    /** LEGACY-STAR-QUOTA: trip creations the balance pays for. */
    @Deprecated
    private Integer unlockedTripSlots;

    /** When the subscription lapses, so the client can say what a downgrade would cost. */
    private java.time.LocalDateTime tierExpiresAt;
}
