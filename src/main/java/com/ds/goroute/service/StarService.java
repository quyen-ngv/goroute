package com.ds.goroute.service;

import com.ds.goroute.dto.response.StarWalletResponse;
import com.ds.goroute.entity.StarTransaction;
import com.ds.goroute.entity.TripCreationEntitlement;
import com.ds.goroute.entity.UserStarWallet;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.StarMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.ds.goroute.constant.ErrorConstant.INVALID_PARAMETERS;

@Service
@RequiredArgsConstructor
public class StarService {
    private static final int FREE_TRIP_QUOTA = 2;
    private static final int TRIP_UNLOCK_COST = 10;
    private static final int TRANSACTION_LIMIT = 50;

    private final StarMapper starMapper;

    @Transactional
    public void reserveTripCreation(UUID userId) {
        ensureWallet(userId);
        UserStarWallet wallet = starMapper.findWallet(userId);
        if (wallet.getFreeTripQuotaUsed() < FREE_TRIP_QUOTA && starMapper.incrementFreeQuota(userId) == 1) {
            return;
        }
        TripCreationEntitlement entitlement = starMapper.findActiveEntitlement(userId, LocalDateTime.now());
        if (entitlement == null || starMapper.consumeEntitlement(entitlement.getId(), LocalDateTime.now()) != 1) {
            throw new BusinessException(INVALID_PARAMETERS,
                    "You have used your 2 free trip slots. Unlock a new trip with 10 stars.");
        }
    }

    @Transactional
    public StarWalletResponse unlockTrip(UUID userId) {
        ensureWallet(userId);
        UserStarWallet wallet = starMapper.findWallet(userId);
        if (wallet.getBalance() < TRIP_UNLOCK_COST) {
            throw new BusinessException(INVALID_PARAMETERS, "You need 10 stars to unlock a trip.");
        }
        String reference = "trip_unlock:" + userId + ":" + UUID.randomUUID();
        starMapper.incrementBalance(userId, -TRIP_UNLOCK_COST);
        starMapper.insertTransaction(StarTransaction.builder()
                .id(UUID.randomUUID()).userId(userId).amount(-TRIP_UNLOCK_COST)
                .transactionType("TRIP_UNLOCK").referenceKey(reference)
                .description("Unlocked one trip creation slot for 3 months").build());
        starMapper.insertEntitlement(TripCreationEntitlement.builder()
                .id(UUID.randomUUID()).userId(userId).starsSpent(TRIP_UNLOCK_COST)
                .expiresAt(LocalDateTime.now().plusMonths(3)).build());
        return getWallet(userId);
    }

    @Transactional
    public boolean grant(UUID userId, int amount, String type, String referenceKey, String description) {
        ensureWallet(userId);
        if (starMapper.countReference(referenceKey) > 0) return false;
        starMapper.incrementBalance(userId, amount);
        starMapper.insertTransaction(StarTransaction.builder()
                .id(UUID.randomUUID()).userId(userId).amount(amount)
                .transactionType(type).referenceKey(referenceKey).description(description).build());
        return true;
    }

    @Transactional
    public StarWalletResponse getWallet(UUID userId) {
        ensureWallet(userId);
        UserStarWallet wallet = starMapper.findWallet(userId);
        return StarWalletResponse.builder()
                .balance(wallet.getBalance()).freeTripQuotaUsed(wallet.getFreeTripQuotaUsed())
                .freeTripQuota(FREE_TRIP_QUOTA)
                .canCreateTrip(wallet.getFreeTripQuotaUsed() < FREE_TRIP_QUOTA || wallet.getBalance() >= TRIP_UNLOCK_COST)
                .starsToUnlockTrip(TRIP_UNLOCK_COST)
                .recentTransactions(starMapper.findTransactions(userId, TRANSACTION_LIMIT)).build();
    }

    @Transactional
    public List<StarTransaction> getTransactions(UUID userId) {
        ensureWallet(userId);
        return starMapper.findTransactions(userId, TRANSACTION_LIMIT);
    }

    @Transactional
    public int awardEligibleTripCompletions() {
        int awarded = 0;
        for (var candidate : starMapper.findEligibleCompletionCandidates()) {
            if (grant(candidate.getOwnerId(), 1, "TRIP_COMPLETED",
                    "completed_trip:" + candidate.getTripId(),
                    "Your public trip met the completion milestones")) {
                awarded++;
            }
        }
        return awarded;
    }

    private void ensureWallet(UUID userId) {
        starMapper.createWallet(userId);
    }
}
