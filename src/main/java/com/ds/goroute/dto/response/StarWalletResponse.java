package com.ds.goroute.dto.response;

import com.ds.goroute.entity.StarTransaction;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StarWalletResponse {
    private int balance;
    private int freeTripQuotaUsed;
    private int freeTripQuota;
    private boolean canCreateTrip;
    private int starsToUnlockTrip;
    /**
     * Slots already paid for and still unused. Without it a client can only say "you have
     * enough stars to unlock", never "you already unlocked one" -- and a user who cannot
     * see what their stars bought will buy it again.
     */
    private int unlockedTripSlots;
    private List<StarTransaction> recentTransactions;
}
