package com.ds.goroute.service.expense;

import com.ds.goroute.entity.Expense;
import com.ds.goroute.entity.ExpensePayer;
import com.ds.goroute.entity.ExpenseSplit;
import com.ds.goroute.entity.Settlement;
import com.ds.goroute.repository.ExpensePayerRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.SettlementRepository;
import com.ds.goroute.service.expense.ExpenseComposition.PayerLine;
import com.ds.goroute.service.expense.ExpenseComposition.SplitLine;
import com.ds.goroute.type.SettlementMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Writes and reads the ledger rows of a trip expense: payers and shares (epic 05 §2.3, §2.4f).
 *
 * <p>One rule keeps old app builds right: a share's {@code is_settled} flag is true exactly when
 * its person is the expense's main payer, or a live settlement linked to the share
 * ({@code settlements.split_id}) records that it was paid. Marking paid writes that settlement,
 * unmarking soft-deletes it, and any edit that changes the share's amount or the payer clears both.
 * The ledger itself reads settlements only.
 */
@Component
@RequiredArgsConstructor
public class ExpenseLedgerStore {

    private final ExpenseSplitRepository splitRepository;
    private final ExpensePayerRepository payerRepository;
    private final SettlementRepository settlementRepository;

    /** Payers read for a list of expenses, keyed by expense id. */
    public record Details(Map<UUID, List<PayerLine>> payers) {

        public List<PayerLine> payers(UUID expenseId) {
            return payers.getOrDefault(expenseId, List.of());
        }
    }

    /** Sets the legacy payer columns old builds and the Wallet read to the main payer. */
    public static void applyPrimaryPayer(Expense expense, ParticipantRef primary, String guestName) {
        if (primary.isGuest()) {
            expense.setPaidBy(primary.guestMemberId());
            expense.setPaidByGuestMemberId(primary.guestMemberId());
            expense.setPaidByGuestName(guestName);
        } else {
            expense.setPaidBy(primary.userId());
            expense.setPaidByGuestMemberId(null);
            expense.setPaidByGuestName(null);
        }
    }

    /** The main payer as the legacy columns name them; null when there is none. */
    public static ParticipantRef primaryPayerOf(Expense expense) {
        if (expense.getPaidByGuestMemberId() != null) return ParticipantRef.guest(expense.getPaidByGuestMemberId());
        return expense.getPaidBy() == null ? null : ParticipantRef.user(expense.getPaidBy());
    }

    /**
     * Replaces the expense's payers and brings its shares in line with the composition. A person already on the expense keeps their
     * share row, so its id and the settlement linked to it survive the edit; the paid flag is kept
     * only while the amount and the main payer are unchanged.
     *
     * @param previousPrimary the main payer before this edit; null on create
     * @return the shares as stored, in composition order
     */
    public List<ExpenseSplit> write(Expense expense, ExpenseComposition composition,
                                    List<ExpenseSplit> previous, ParticipantRef previousPrimary) {
        UUID expenseId = expense.getId();
        ParticipantRef primary = composition.primaryPayer();
        LocalDateTime now = LocalDateTime.now();

        payerRepository.deleteByExpenseId(expenseId);
        List<ExpensePayer> payerRows = new ArrayList<>();
        int position = 0;
        for (PayerLine payer : composition.payers()) {
            payerRows.add(ExpensePayer.builder()
                    .id(UUID.randomUUID())
                    .expenseId(expenseId)
                    .userId(payer.participant().userId())
                    .guestMemberId(payer.participant().guestMemberId())
                    .amount(payer.amount())
                    .position(position++)
                    .build());
        }
        payerRepository.saveAll(payerRows);

        boolean samePayer = Objects.equals(previousPrimary, primary);
        List<ExpenseSplit> pool = new ArrayList<>(previous == null ? List.of() : previous);
        List<UUID> staleLinks = new ArrayList<>();
        List<ExpenseSplit> stored = new ArrayList<>();
        for (SplitLine line : composition.splits()) {
            ExpenseSplit before = take(pool, line);
            boolean isPayersOwn = line.participant() != null && line.participant().equals(primary);
            boolean keepsPaid = before != null && Boolean.TRUE.equals(before.getIsSettled())
                    && before.getAmount() != null && before.getAmount().compareTo(line.amount()) == 0
                    && samePayer;
            boolean settled = isPayersOwn || keepsPaid;
            LocalDateTime settledAt = keepsPaid && before.getSettledAt() != null ? before.getSettledAt()
                    : settled ? now : null;
            if (before != null && Boolean.TRUE.equals(before.getIsSettled()) && !keepsPaid) {
                staleLinks.add(before.getId());
            }
            if (before != null) {
                before.setAmount(line.amount());
                before.setShareInput(line.shareInput());
                before.setGuestName(line.participant() != null && !line.participant().isGuest() ? null : line.guestName());
                before.setIsSettled(settled);
                before.setSettledAt(settledAt);
                splitRepository.update(before);
                stored.add(before);
                continue;
            }
            ExpenseSplit split = ExpenseSplit.builder()
                    .id(UUID.randomUUID())
                    .expenseId(expenseId)
                    .userId(line.participant() == null ? null : line.participant().userId())
                    .guestMemberId(line.participant() == null ? null : line.participant().guestMemberId())
                    .guestName(line.participant() != null && !line.participant().isGuest() ? null : line.guestName())
                    .amount(line.amount())
                    .shareInput(line.shareInput())
                    .isSettled(settled)
                    .settledAt(settledAt)
                    .build();
            splitRepository.save(split);
            stored.add(split);
        }
        for (ExpenseSplit gone : pool) {
            if (Boolean.TRUE.equals(gone.getIsSettled())) staleLinks.add(gone.getId());
        }
        // Before the rows go: deleting a share nulls settlements.split_id, losing the link.
        settlementRepository.softDeleteBySplitIds(staleLinks);
        pool.forEach(gone -> splitRepository.delete(gone.getId()));
        return stored;
    }

    /** Before an expense is deleted: the payments its old-style "paid" marks stood for go with it. */
    public void beforeDelete(List<ExpenseSplit> splits) {
        settlementRepository.softDeleteBySplitIds(splits.stream().map(ExpenseSplit::getId).toList());
    }

    /**
     * An old build's "mark as paid" / "not paid" for these shares. The main payer's own share
     * always stays paid and has no settlement; any other identified share gets (or loses) the
     * settlement from its person to the main payer for its amount.
     *
     * @return the shares, updated
     */
    public List<ExpenseSplit> settleLegacy(Expense expense, List<ExpenseSplit> splits, boolean paid, UUID actorId) {
        ParticipantRef primary = primaryPayerOf(expense);
        Set<UUID> linked = settlementRepository.findActiveBySplitIds(splits.stream().map(ExpenseSplit::getId).toList())
                .stream().map(Settlement::getSplitId).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        List<UUID> unlink = new ArrayList<>();
        for (ExpenseSplit split : splits) {
            ParticipantRef participant = ParticipantRef.of(split.getUserId(), split.getGuestMemberId());
            boolean isPayersOwn = participant != null && participant.equals(primary);
            boolean settled = isPayersOwn || paid;
            split.setIsSettled(settled);
            split.setSettledAt(settled ? now : null);
            splitRepository.update(split);
            if (isPayersOwn) continue;
            if (!paid) {
                unlink.add(split.getId());
                continue;
            }
            boolean canRecord = participant != null && primary != null && !linked.contains(split.getId())
                    && split.getAmount() != null && split.getAmount().signum() > 0
                    // Only trips have a ledger; an expense outside trips keeps the flag alone.
                    && expense.getTripId() != null;
            if (!canRecord) continue;
            settlementRepository.insert(Settlement.builder()
                    .id(UUID.randomUUID())
                    .tripId(expense.getTripId())
                    .fromUserId(participant.userId())
                    .fromGuestMemberId(participant.guestMemberId())
                    .toUserId(primary.userId())
                    .toGuestMemberId(primary.guestMemberId())
                    .amount(split.getAmount())
                    .currency(expense.getCurrency())
                    .method(SettlementMethod.OTHER)
                    .paidAt(now)
                    .splitId(split.getId())
                    .createdBy(actorId)
                    .build());
        }
        settlementRepository.softDeleteBySplitIds(unlink);
        return splits;
    }

    /** Payers for these expenses, in one query. */
    public Details details(Collection<Expense> expenses) {
        return new Details(payersOf(expenses));
    }

    /** Payers per expense, in one query; an expense with no payer row falls back to its legacy payer. */
    public Map<UUID, List<PayerLine>> payersOf(Collection<Expense> expenses) {
        Map<UUID, List<PayerLine>> payers = new LinkedHashMap<>();
        if (expenses.isEmpty()) return payers;
        for (ExpensePayer row : payerRepository.findByExpenseIds(expenses.stream().map(Expense::getId).toList())) {
            ParticipantRef participant = ParticipantRef.of(row.getUserId(), row.getGuestMemberId());
            if (participant == null) continue;
            payers.computeIfAbsent(row.getExpenseId(), ignored -> new ArrayList<>())
                    .add(new PayerLine(participant, row.getAmount()));
        }
        // A row written before V268's backfill, or by a path that missed it, still has its payer.
        for (Expense expense : expenses) {
            ParticipantRef legacy = primaryPayerOf(expense);
            if (!payers.containsKey(expense.getId()) && legacy != null && expense.getAmount() != null) {
                payers.put(expense.getId(), List.of(new PayerLine(legacy, expense.getAmount())));
            }
        }
        return payers;
    }

    /** The ledger's view of one expense: payers and identified shares in its currency. */
    public static LedgerCalculator.ExpenseLine ledgerLine(Expense expense, List<PayerLine> payers,
                                                          List<ExpenseSplit> splits) {
        List<LedgerCalculator.Amount> paid = payers.stream()
                .filter(payer -> payer.participant() != null && payer.amount() != null)
                .map(payer -> new LedgerCalculator.Amount(payer.participant(), payer.amount()))
                .toList();
        List<LedgerCalculator.Amount> owed = new ArrayList<>();
        for (ExpenseSplit split : splits) {
            ParticipantRef participant = ParticipantRef.of(split.getUserId(), split.getGuestMemberId());
            if (participant == null || split.getAmount() == null) continue;
            owed.add(new LedgerCalculator.Amount(participant, split.getAmount()));
        }
        return new LedgerCalculator.ExpenseLine(expense.getCurrency(), paid, owed);
    }

    /** Removes and returns the previous share of the same person (or the same name, for a name-only share). */
    private static ExpenseSplit take(List<ExpenseSplit> pool, SplitLine line) {
        for (Iterator<ExpenseSplit> it = pool.iterator(); it.hasNext(); ) {
            ExpenseSplit candidate = it.next();
            ParticipantRef participant = ParticipantRef.of(candidate.getUserId(), candidate.getGuestMemberId());
            boolean same = line.participant() != null
                    ? line.participant().equals(participant)
                    : participant == null && candidate.getGuestName() != null && line.guestName() != null
                      && candidate.getGuestName().trim().equalsIgnoreCase(line.guestName().trim());
            if (same) {
                it.remove();
                return candidate;
            }
        }
        return null;
    }

    /** Every person already on these rows, so an edit may keep someone who has since left the trip. */
    public static Set<ParticipantRef> participantsOf(List<PayerLine> payers, List<ExpenseSplit> splits) {
        Set<ParticipantRef> people = new HashSet<>();
        payers.forEach(payer -> people.add(payer.participant()));
        splits.forEach(split -> people.add(ParticipantRef.of(split.getUserId(), split.getGuestMemberId())));
        people.remove(null);
        return people;
    }

    /** Payers by amount and person; used to tell whether an edit changed who paid. */
    public static Map<ParticipantRef, BigDecimal> payerAmounts(List<PayerLine> payers) {
        Map<ParticipantRef, BigDecimal> amounts = new HashMap<>();
        payers.forEach(payer -> amounts.merge(payer.participant(), payer.amount(), BigDecimal::add));
        return amounts;
    }
}
