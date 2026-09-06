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

    private static final int TRIP_UNLOCK_COST = 10;
    private static final int TRANSACTION_LIMIT = 50;

    private final StarMapper starMapper;
    private final StarWalletBootstrapService walletBootstrap;
    private final UserQuotaPolicyService quotaPolicy;

    @Transactional
    public void reserveTripCreation(UUID userId) {
        UserStarWallet wallet = ensureWallet(userId);
        int freeTripQuota = quotaPolicy.freeTripQuota(userId);
        int freeTripQuotaUsed = wallet.getFreeTripQuotaUsed() != null ? wallet.getFreeTripQuotaUsed() : 0;
        if (freeTripQuotaUsed < freeTripQuota
                && starMapper.incrementFreeQuota(userId, freeTripQuota) == 1) {
            return;
        }
        TripCreationEntitlement entitlement = starMapper.findActiveEntitlement(userId, LocalDateTime.now());
        if (entitlement == null || starMapper.consumeEntitlement(entitlement.getId(), LocalDateTime.now()) != 1) {
            throw new BusinessException(ErrorConstant.TRIP_CREATION_QUOTA_EXHAUSTED);
        }
    }

    /**
     * How a trip creation would go right now, without consuming anything.
     *
     * <p>Exists so a caller that is about to spend something <em>else</em> on the user's behalf --
     * an AI generation slot, and the model bill behind it -- can find out first. Asking
     * {@link #reserveTripCreation} and rolling back is not the same thing: the AI flow reserves its
     * own quota in a separate short transaction, so a rollback here would not give that back.
     */
    @Transactional
    public TripCreationStatus tripCreationStatus(UUID userId) {
        UserStarWallet wallet = ensureWallet(userId);
        int unlockedSlots = starMapper.countActiveEntitlements(userId, LocalDateTime.now());
        int freeTripQuota = quotaPolicy.freeTripQuota(userId);
        int freeTripQuotaUsed = wallet.getFreeTripQuotaUsed() != null ? wallet.getFreeTripQuotaUsed() : 0;
        return new TripCreationStatus(
                freeTripQuotaUsed < freeTripQuota || unlockedSlots > 0,
                freeTripQuotaUsed,
                freeTripQuota,
                unlockedSlots);
    }

    /**
     * @param available whether the next trip creation would be accepted, which is not the same as
     *                  having enough stars to buy the right to one
     */
    public record TripCreationStatus(boolean available, int freeQuotaUsed, int freeQuota,
                                     int unlockedSlots) {
    }

    /**
     * Buys one trip creation slot.
     *
     * <p>The reference key is the position in this user's own sequence of unlocks, not a
     * fresh UUID. A random key is unique by construction and therefore idempotent against
     * nothing: two taps on the same button, or a retry after a timeout the client never
     * saw resolve, each bought their own slot at full price. Counting instead means both
     * attempts build the same key, the second one loses to the unique index, and the
     * entitlement is written only for the attempt that actually paid.
     */
    @Transactional
    public StarWalletResponse unlockTrip(UUID userId) {
        ensureWallet(userId);
        String reference = "trip_unlock:" + userId + ":" + starMapper.countEntitlements(userId);
        record(userId, -TRIP_UNLOCK_COST, "TRIP_UNLOCK", reference,
                "Unlocked one trip creation slot for 3 months", null, null, null)
                .ifPresent(charged -> starMapper.insertEntitlement(TripCreationEntitlement.builder()
                        .id(UUID.randomUUID()).userId(userId).starsSpent(TRIP_UNLOCK_COST)
                        .expiresAt(LocalDateTime.now().plusMonths(3)).build()));
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

    /** The balance alone, for callers that do not need the wallet's whole picture. */
    @Transactional
    public int getBalance(UUID userId) {
        UserStarWallet wallet = ensureWallet(userId);
        return wallet.getBalance() != null ? wallet.getBalance() : 0;
    }

    @Transactional
    public StarWalletResponse getWallet(UUID userId) {
        UserStarWallet wallet = ensureWallet(userId);
        TripCreationStatus tripQuota = tripCreationStatus(userId);
        int balance = wallet.getBalance() != null ? wallet.getBalance() : 0;
        int freeTripQuotaUsed = wallet.getFreeTripQuotaUsed() != null ? wallet.getFreeTripQuotaUsed() : 0;
        return StarWalletResponse.builder()
                .balance(balance).freeTripQuotaUsed(freeTripQuotaUsed)
                .freeTripQuota(tripQuota.freeQuota())
                // Whether a trip can be created right now, which is not the same as having
                // enough stars to buy the right to: an affordable unlock still has to be
                // bought. Answering the second question here is what let the client show a
                // green tick to somebody whose next trip creation would be refused.
                .canCreateTrip(tripQuota.available())
                .starsToUnlockTrip(TRIP_UNLOCK_COST)
                .unlockedTripSlots(tripQuota.unlockedSlots())
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
        if (wallet == null) {
            log.error("Wallet for update not found for user: {}", userId);
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Star wallet not found");
        }

        // Read under the lock, so this is not the check-then-insert race it looks like:
        // every entry for this user is serialised behind the same wallet row. It matters
        // because in PostgreSQL a unique-index violation aborts the whole transaction --
        // catching it below leaves nothing else able to run, and a retried job would take
        // the rest of its work down with it. The index stays as the backstop; this is the
        // path a replay is expected to take.
        if (starMapper.countReference(referenceKey) > 0) {
            return Optional.empty();
        }

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

    /**
     * Creates the wallet only when this transaction cannot already see one.
     *
     * <p>The check is not an optimisation. {@link StarWalletBootstrapService#ensureExists} runs
     * REQUIRES_NEW, so on a second connection -- and once {@link #record} has taken the wallet
     * row lock, its {@code INSERT ... ON CONFLICT DO NOTHING} waits on the tuple our own
     * suspended transaction holds. PostgreSQL sees no cycle to break (the outer transaction is
     * idle, waiting on nobody), so the request hangs until the JDBC socket timeout kills the
     * connection. Reading first means the bootstrap only ever runs when no lock can exist yet.
     */
    private UserStarWallet ensureWallet(UUID userId) {
        UserStarWallet wallet = starMapper.findWallet(userId);
        if (wallet != null) {
            return wallet;
        }
        walletBootstrap.ensureExists(userId);
        wallet = starMapper.findWalletAfterBootstrap(userId);
        if (wallet == null) {
            log.error("Failed to load or initialize star wallet for user: {}", userId);
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Star wallet not found");
        }
        return wallet;
    }

    /** Reads an entry by its idempotency key, for callers that need to see the original. */
    @Transactional(readOnly = true)
    public Optional<StarTransaction> findByReference(String referenceKey) {
        return Optional.ofNullable(starMapper.findTransactionByReference(referenceKey));
    }
}
