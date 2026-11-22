package com.ds.goroute.service;

import com.ds.goroute.dto.response.AiCreditWalletResponse;
import com.ds.goroute.dto.response.StarWalletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** The Star wallet as the app shows it, next to what its Stars are worth in credits. */
@Service
@RequiredArgsConstructor
public class StarWalletService {
    private static final int TRANSACTION_LIMIT = 50;

    private final StarService stars;
    private final AiCreditService credits;

    @Transactional
    public StarWalletResponse wallet(UUID userId) {
        int balance = stars.getBalance(userId);
        AiCreditWalletResponse credit = credits.wallet(userId);
        int cost = credit.getTripCreationCost();
        int rate = credit.getStarExchange().getRate();
        BigDecimal creditBalance = credit.getBalance();
        return StarWalletResponse.builder()
                .balance(balance)
                .creditBalance(creditBalance)
                .tripCreationCost(cost)
                .exchange(credit.getStarExchange())
                .recentTransactions(stars.getTransactions(userId, TRANSACTION_LIMIT, 0))
                .freeTripQuota(0)
                .freeTripQuotaUsed(0)
                .canCreateTrip(credit.isUnlimited() || creditBalance.compareTo(BigDecimal.valueOf(cost)) >= 0)
                .starsToUnlockTrip(starsForOneTrip(cost, rate))
                .unlockedTripSlots(cost <= 0 ? 0 : creditBalance.max(BigDecimal.ZERO)
                        .divide(BigDecimal.valueOf(cost), 0, RoundingMode.FLOOR).intValue())
                .build();
    }

    @Transactional
    public StarWalletResponse exchange(UUID userId, int starCount) {
        credits.exchangeStars(userId, starCount);
        return wallet(userId);
    }

    /**
     * LEGACY-STAR-QUOTA: {@code POST /me/stars/unlock-trip} from app builds up to 21. It no longer
     * buys a slot; it exchanges the Stars one trip's credits cost, under the same monthly cap, so the
     * old button still works and is no way around the cap. Delete with the endpoint once
     * APP.MIN_BUILD_* passes the first credit build.
     */
    @Deprecated
    @Transactional
    public StarWalletResponse unlockTrip(UUID userId) {
        AiCreditWalletResponse credit = credits.wallet(userId);
        int starCount = starsForOneTrip(credit.getTripCreationCost(), credit.getStarExchange().getRate());
        if (starCount > 0) credits.exchangeStars(userId, starCount);
        return wallet(userId);
    }

    private static int starsForOneTrip(int cost, int rate) {
        if (cost <= 0 || rate <= 0) return 0;
        return (cost + rate - 1) / rate;
    }
}
