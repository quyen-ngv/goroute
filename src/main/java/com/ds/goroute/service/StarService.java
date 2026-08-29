package com.ds.goroute.service;

import com.ds.goroute.dto.response.StarWalletResponse;
import com.ds.goroute.entity.StarTransaction;
import com.ds.goroute.entity.TripCreationEntitlement;
import com.ds.goroute.entity.UserStarWallet;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.StarMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ds.goroute.constant.ErrorConstant;

/**
 * The one point wallet for the whole product (REWARD-01).
 *
 * <p>Passport rewards, check-in bonuses, referrals and trip unlocks all move through here.
 * Nothing gets a wallet of its own -- a second balance is a second answer to "how many
 * points do I have", and users notice.
 *
 * <p>Four rules hold everywhere, and each one is enforced rather than assumed:
 * <ul>
 *   <li>A balance is never negative. The database has a check constraint saying so.</li>
 *   <li>The same balance cannot be spent twice, including from two devices at once: every
 *       spend reads the wallet row locked.</li>
 *   <li>A refund is a reverse entry. History is append-only, because a ledger that can be
 *       edited cannot be reconciled against anything.</li>
 *   <li>The same reference key never produces two entries. A unique index enforces it, so
 *       a retry cannot slip between a check and an insert.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StarService {

    private static final int FREE_TRIP_QUOTA = 3;
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
            throw new BusinessException(ErrorConstant.TRIP_CREATION_QUOTA_EXHAUSTED);
        }
    }

    @Transactional
    public StarWalletResponse unlockTrip(UUID userId) {
        String reference = "trip_unlock:" + userId + ":" + UUID.randomUUID();
        spend(userId, TRIP_UNLOCK_COST, "TRIP_UNLOCK", reference,
                "Unlocked one trip creation slot for 3 months");
        starMapper.insertEntitlement(TripCreationEntitlement.builder()
                .id(UUID.randomUUID()).userId(userId).starsSpent(TRIP_UNLOCK_COST)
                .expiresAt(LocalDateTime.now().plusMonths(3)).build());
        return getWallet(userId);
    }

    /**
     * Adds points. Returns {@code false} when this reference has already been credited,
     * which is what makes a retried job safe to run again.
     */
    @Transactional
    public boolean grant(UUID userId, int amount, String type, String referenceKey, String description) {
        if (amount <= 0) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "A grant must be positive");
        }
        ensureWallet(userId);
        return record(userId, amount, type, referenceKey, description, null, null, null).isPresent();
    }

    /**
     * Removes points, refusing when the balance is not enough.
     *
     * @return the ledger entry, so a caller can attach whatever it bought to it
     */
    @Transactional
    public StarTransaction spend(UUID userId, int amount, String type, String referenceKey, String description) {
        if (amount <= 0) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "A spend must be positive");
        }
        ensureWallet(userId);
        // record() takes the row lock and refuses to go below zero, so two simultaneous
        // redemptions cannot both see the same balance and both spend it.
        // Already spent under this key: return the original rather than charging twice.
        return record(userId, -amount, type, referenceKey, description, null, null, null)
                .orElseGet(() -> starMapper.findTransactionByReference(referenceKey));
    }

    /**
     * Gives back what a transaction took, as a new opposite entry. Reversing the same
     * transaction twice is refused; reversing something that was itself a reversal is too.
     */
    @Transactional
    public StarTransaction refund(UUID userId, UUID transactionId, String reason) {
        StarTransaction original = starMapper.findTransactionById(transactionId);
        if (original == null || !original.getUserId().equals(userId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Transaction not found");
        }
        if (original.getReversesTransactionId() != null) {
            throw new BusinessException(ErrorConstant.POINT_TRANSACTION_NOT_REVERSIBLE,
                    "This transaction cannot be reversed.");
        }
        ensureWallet(userId);
        return record(userId, -original.getAmount(), "REFUND",
                "refund:" + original.getId(), "Refund of " + original.getTransactionType(),
                original.getId(), null, reason)
                .orElseThrow(() -> new BusinessException(ErrorConstant.POINT_TRANSACTION_NOT_REVERSIBLE,
                        "This transaction has already been reversed."));
    }

    /**
     * Operator correction, for complaint handling and incident compensation. An ordinary
     * ledger entry with its own source -- not a side door that skips the rules.
     */
    @Transactional
    public StarTransaction adjust(UUID userId, int amount, UUID operatorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "An adjustment needs a reason");
        }
        ensureWallet(userId);
        // A negative result is refused inside record(), operator or not.
        return record(userId, amount, "ADMIN_ADJUSTMENT",
                "adjustment:" + userId + ":" + UUID.randomUUID(), "Operator adjustment",
                null, operatorId, reason)
                .orElseThrow(() -> new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                        "That adjustment was already recorded."));
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
                .recentTransactions(starMapper.findTransactions(userId, TRANSACTION_LIMIT, 0)).build();
    }

    @Transactional
    public List<StarTransaction> getTransactions(UUID userId, int limit, int offset) {
        ensureWallet(userId);
        return starMapper.findTransactions(userId, Math.max(1, Math.min(limit, 100)), Math.max(0, offset));
    }

    @Transactional(readOnly = true)
    public long countTransactions(UUID userId) {
        return starMapper.countTransactions(userId);
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

    /**
     * Wallets whose stored balance disagrees with the sum of their entries. Without a
     * periodic look, a discrepancy stays invisible until somebody complains.
     */
    @Transactional(readOnly = true)
    public List<UUID> findWalletsOutOfBalance(int limit) {
        return starMapper.findWalletsOutOfBalance(Math.max(1, Math.min(limit, 500)));
    }

    /**
     * Writes the entry and then moves the balance, both under the wallet row lock.
     *
     * <p>The order matters. The entry is inserted first, so that a replayed reference key
     * hits the unique index <em>before</em> anything touches the balance; doing it the
     * other way round leaves the balance moved and the entry rejected, which is exactly
     * the drift the ledger exists to make impossible.
     *
     * @return empty when this reference key was already recorded, so a retry reads as
     *         "already done" rather than as a failure
     */
    private Optional<StarTransaction> record(UUID userId, int amount, String type, String referenceKey,
                                             String description, UUID reversesTransactionId,
                                             UUID operatorId, String reason) {
        UserStarWallet wallet = starMapper.findWalletForUpdate(userId);
        int balanceAfter = wallet.getBalance() + amount;
        if (balanceAfter < 0) {
            throw new BusinessException(ErrorConstant.INSUFFICIENT_POINTS, "You do not have enough points.");
        }

        StarTransaction transaction = StarTransaction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .amount(amount)
                .transactionType(type)
                .referenceKey(referenceKey)
                .description(description)
                .balanceAfter(balanceAfter)
                .reversesTransactionId(reversesTransactionId)
                .createdBy(operatorId)
                .reason(reason)
                .build();
        try {
            starMapper.insertTransaction(transaction);
        } catch (DuplicateKeyException exception) {
            return Optional.empty();
        }
        starMapper.incrementBalance(userId, amount);
        return Optional.of(transaction);
    }

    private void ensureWallet(UUID userId) {
        starMapper.createWallet(userId);
    }

    /** Reads an entry by its idempotency key, for callers that need to see the original. */
    @Transactional(readOnly = true)
    public Optional<StarTransaction> findByReference(String referenceKey) {
        return Optional.ofNullable(starMapper.findTransactionByReference(referenceKey));
    }
}
