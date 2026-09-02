package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What an AI trip generation would cost the user, both halves of it.
 *
 * <p>An AI generation spends two different allowances: a generation slot up front, and a
 * trip-creation slot at the end when the itinerary is saved as a real trip. Reporting only the
 * first is what let somebody start a generation they could never finish -- the AI slot gone, the
 * model bill paid, and a refusal at the last step. The client needs both numbers to say so before
 * anything is spent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTripUsage {
    private String tier;
    private Integer used;
    private Integer limit;

    /** True only when both allowances have room. */
    private Boolean eligible;

    /** Generation slots alone, ignoring the trip-creation side. */
    private Boolean aiQuotaAvailable;

    /** Trip-creation slots alone: a free slot left, or an unlocked one not yet used. */
    private Boolean tripQuotaAvailable;

    private Integer freeTripQuotaUsed;
    private Integer freeTripQuota;
    private Integer unlockedTripSlots;

    /** When the subscription lapses, so the client can say what a downgrade would cost. */
    private java.time.LocalDateTime tierExpiresAt;
}
