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
    private List<StarTransaction> recentTransactions;
}
