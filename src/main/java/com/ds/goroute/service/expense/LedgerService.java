package com.ds.goroute.service.expense;

import com.ds.goroute.dto.response.TripLedgerResponse;
import com.ds.goroute.dto.response.WalletPersonResponse;
import com.ds.goroute.entity.Expense;
import com.ds.goroute.entity.ExpenseSplit;
import com.ds.goroute.entity.Settlement;
import com.ds.goroute.repository.ExpenseRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.SettlementRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.expense.ExpenseComposition.PayerLine;
import com.ds.goroute.service.expense.LedgerCalculator.CurrencyLedger;
import com.ds.goroute.service.expense.LedgerCalculator.ExpenseLine;
import com.ds.goroute.service.expense.LedgerCalculator.Payment;
import com.ds.goroute.service.expense.LedgerCalculator.Transfer;
import com.ds.goroute.type.TripRealtimeEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A trip's balances (epic 05 §2.4f): computed on every read from expense payers, shares and
 * live settlements, per currency, never from {@code expense_splits.is_settled}.
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final SettlementRepository settlementRepository;
    private final TripRepository tripRepository;
    private final TripAccessGuard tripAccessGuard;
    private final ExpenseLedgerStore ledgerStore;
    private final ExpenseParticipants participants;
    private final TripRealtimePublisher tripRealtimePublisher;

    /** Expenses and payments of one context, ready for {@link LedgerCalculator}. */
    record Book(List<Expense> expenses, Map<UUID, ExpenseLine> lines, List<Payment> payments,
                List<Settlement> settlements) {

        List<ExpenseLine> expenseLines() {
            return new ArrayList<>(lines.values());
        }
    }

    @Transactional(readOnly = true)
    public TripLedgerResponse tripLedger(UUID tripId, UUID userId) {
        tripAccessGuard.requireAccess(tripId, userId);
        boolean simplify = tripRepository.findSimplifyDebts(tripId);
        Book book = book(expenseRepository.findByTripId(tripId), settlementRepository.findActiveByTripIds(List.of(tripId)));
        Map<String, CurrencyLedger> balances = LedgerCalculator.balances(book.expenseLines(), book.payments());
        Map<String, List<Transfer>> transfers = transfers(book, balances, simplify);

        Set<ParticipantRef> refs = new LinkedHashSet<>();
        balances.values().forEach(ledger -> refs.addAll(ledger.members().keySet()));
        Map<ParticipantRef, WalletPersonResponse> people = participants.describe(refs);

        List<TripLedgerResponse.CurrencyBlock> blocks = new ArrayList<>();
        balances.forEach((currency, ledger) -> blocks.add(TripLedgerResponse.CurrencyBlock.builder()
                .currency(currency)
                .members(ledger.members().entrySet().stream().map(entry -> TripLedgerResponse.Member.builder()
                        .participant(ExpenseParticipants.person(entry.getKey(), people))
                        .paid(entry.getValue().paid())
                        .owed(entry.getValue().owed())
                        .settledOut(entry.getValue().settledOut())
                        .settledIn(entry.getValue().settledIn())
                        .net(entry.getValue().net())
                        .build()).toList())
                .transfers(transfers.getOrDefault(currency, List.of()).stream()
                        .map(transfer -> TripLedgerResponse.Transfer.builder()
                                .from(ExpenseParticipants.person(transfer.from(), people))
                                .to(ExpenseParticipants.person(transfer.to(), people))
                                .amount(transfer.amount())
                                .build()).toList())
                .build()));
        return TripLedgerResponse.builder().tripId(tripId).simplifyDebts(simplify).currencies(blocks).build();
    }

    /** Owner or editor; "simplify debts" is per trip and on by default. */
    @Transactional
    public TripLedgerResponse updateSettings(UUID tripId, boolean simplifyDebts, UUID userId) {
        tripAccessGuard.requireEditAccess(tripId, userId);
        tripRepository.updateSimplifyDebts(tripId, simplifyDebts);
        tripRealtimePublisher.publishAfterCommit(TripRealtimeEventType.LEDGER_UPDATED, tripId, tripId, userId);
        return tripLedger(tripId, userId);
    }

    private Map<String, List<Transfer>> transfers(Book book, Map<String, CurrencyLedger> balances, boolean simplify) {
        Map<String, List<Transfer>> transfers = new LinkedHashMap<>();
        balances.forEach((currency, ledger) -> transfers.put(currency, simplify
                ? LedgerCalculator.simplify(ledger.nets())
                : LedgerCalculator.pairwise(book.expenseLines(), book.payments(), currency,
                        SplitCalculator.fractionDigits(currency, null))));
        return transfers;
    }

    private Book book(List<Expense> expenses, List<Settlement> settlements) {
        return book(expenses, settlements, ledgerStore.payersOf(expenses), splitsOf(expenses));
    }

    private static Book book(List<Expense> expenses, List<Settlement> settlements,
                             Map<UUID, List<PayerLine>> payers, Map<UUID, List<ExpenseSplit>> splits) {
        Map<UUID, ExpenseLine> lines = new LinkedHashMap<>();
        for (Expense expense : expenses) {
            lines.put(expense.getId(), ExpenseLedgerStore.ledgerLine(expense,
                    payers.getOrDefault(expense.getId(), List.of()), splits.getOrDefault(expense.getId(), List.of())));
        }
        List<Payment> payments = new ArrayList<>();
        for (Settlement settlement : settlements) {
            ParticipantRef from = SettlementService.from(settlement);
            ParticipantRef to = SettlementService.to(settlement);
            if (from == null || to == null || settlement.getAmount() == null) continue;
            payments.add(new Payment(from, to, settlement.getAmount(), settlement.getCurrency()));
        }
        return new Book(expenses, lines, payments, settlements);
    }

    private Map<UUID, List<ExpenseSplit>> splitsOf(List<Expense> expenses) {
        return expenseSplitRepository.findByExpenseIds(expenses.stream().map(Expense::getId).toList()).stream()
                .collect(Collectors.groupingBy(ExpenseSplit::getExpenseId));
    }
}
