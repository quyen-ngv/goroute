package com.ds.goroute.dto.response;

import com.ds.goroute.entity.StarTransaction;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class StarWalletResponse {
    private int balance;
    /** The credit balance the exchange adds to. */
    private BigDecimal creditBalance;
    /** Credits one trip creation costs (AI_CREDIT.TRIP_CREATION_COST). */
    private int tripCreationCost;
    private StarExchangeTerms exchange;
    private List<StarTransaction> recentTransactions;

    // LEGACY-STAR-QUOTA: the trip-slot fields app builds up to 21 read. Since V262 they are worked
    // out from credits (free quota 0, slots = trips the credit balance pays for). Delete once
    // APP.MIN_BUILD_* passes the first credit build.

    /** LEGACY-STAR-QUOTA: always 0. */
    @Deprecated
    private int freeTripQuotaUsed;
    /** LEGACY-STAR-QUOTA: always 0. */
    @Deprecated
    private int freeTripQuota;
    /** LEGACY-STAR-QUOTA: the credit balance covers one trip creation (always true for beta). */
    @Deprecated
    private boolean canCreateTrip;
    /** LEGACY-STAR-QUOTA: Stars the old "unlock" button exchanges, enough credits for one trip. */
    @Deprecated
    private int starsToUnlockTrip;
    /** LEGACY-STAR-QUOTA: trip creations the credit balance pays for. */
    @Deprecated
    private int unlockedTripSlots;
}
