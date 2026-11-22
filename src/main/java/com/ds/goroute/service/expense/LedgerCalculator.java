package com.ds.goroute.service.expense;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Balances and "who pays whom" from expenses and settlements (epic 05 §2.4f). Pure: no I/O.
 *
 * <p>For each currency, separately and never converted:
 * {@code net = paid − owed + settledOut − settledIn}; positive means others owe this person.
 * The nets of a currency always add up to zero: every expense charges exactly what was paid
 * (a share that names nobody stays with the first payer) and every settlement moves the same
 * amount out of one person and into another.
 */
public final class LedgerCalculator {

    /** Internal precision for pro-rata debts between several payers, before the final rounding. */
    private static final int WORK_SCALE = 10;

    private LedgerCalculator() {
    }

    public record Amount(ParticipantRef participant, BigDecimal amount) {
    }

    /**
     * One expense: who paid how much and who bears how much. {@code owed} lists identified people
     * only; whatever the paid total leaves unassigned is borne by the first payer.
     */
    public record ExpenseLine(String currency, List<Amount> payers, List<Amount> owed) {
    }

    /** Money handed from one person to another. */
    public record Payment(ParticipantRef from, ParticipantRef to, BigDecimal amount, String currency) {
    }

    public record Transfer(ParticipantRef from, ParticipantRef to, BigDecimal amount) {
    }

    /** One person's totals in one currency. */
    public static final class Balance {
        private BigDecimal paid = BigDecimal.ZERO;
        private BigDecimal owed = BigDecimal.ZERO;
        private BigDecimal settledOut = BigDecimal.ZERO;
        private BigDecimal settledIn = BigDecimal.ZERO;

        public BigDecimal paid() { return paid; }
        public BigDecimal owed() { return owed; }
        public BigDecimal settledOut() { return settledOut; }
        public BigDecimal settledIn() { return settledIn; }

        public BigDecimal net() {
            return paid.subtract(owed).add(settledOut).subtract(settledIn);
        }
    }

    /** Totals per participant, participants in the order they first appear. */
    public record CurrencyLedger(String currency, Map<ParticipantRef, Balance> members) {
        public Map<ParticipantRef, BigDecimal> nets() {
            Map<ParticipantRef, BigDecimal> nets = new LinkedHashMap<>();
            members.forEach((participant, balance) -> nets.put(participant, balance.net()));
            return nets;
        }
    }

    /** Balances per currency, currencies in alphabetical order. */
    public static Map<String, CurrencyLedger> balances(List<ExpenseLine> expenses, List<Payment> payments) {
        Map<String, CurrencyLedger> ledgers = new TreeMap<>();
        for (ExpenseLine expense : expenses) {
            List<Amount> owed = effectiveOwed(expense);
            if (owed == null) continue;
            CurrencyLedger ledger = ledgers.computeIfAbsent(expense.currency(),
                    currency -> new CurrencyLedger(currency, new LinkedHashMap<>()));
            for (Amount payer : expense.payers()) {
                Balance balance = balance(ledger, payer.participant());
                balance.paid = balance.paid.add(payer.amount());
            }
            for (Amount share : owed) {
                Balance balance = balance(ledger, share.participant());
                balance.owed = balance.owed.add(share.amount());
            }
        }
        for (Payment payment : payments) {
            CurrencyLedger ledger = ledgers.computeIfAbsent(payment.currency(),
                    currency -> new CurrencyLedger(currency, new LinkedHashMap<>()));
            Balance from = balance(ledger, payment.from());
            from.settledOut = from.settledOut.add(payment.amount());
            Balance to = balance(ledger, payment.to());
            to.settledIn = to.settledIn.add(payment.amount());
        }
        return ledgers;
    }

    /**
     * The fewest transfers that settle these nets: the largest debtor pays the largest creditor
     * until both sides are empty. At most n − 1 transfers; ties break on the participant key,
     * so the same balances always give the same list. Creates nothing; it is a suggestion.
     */
    public static List<Transfer> simplify(Map<ParticipantRef, BigDecimal> nets) {
        List<Entry> creditors = new ArrayList<>();
        List<Entry> debtors = new ArrayList<>();
        nets.forEach((participant, net) -> {
            if (net.signum() > 0) creditors.add(new Entry(participant, net));
            if (net.signum() < 0) debtors.add(new Entry(participant, net.negate()));
        });
        List<Transfer> transfers = new ArrayList<>();
        Comparator<Entry> largestFirst = Comparator.comparing((Entry entry) -> entry.amount).reversed()
                .thenComparing(entry -> entry.participant.key());
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            creditors.sort(largestFirst);
            debtors.sort(largestFirst);
            Entry creditor = creditors.get(0);
            Entry debtor = debtors.get(0);
            BigDecimal amount = creditor.amount.min(debtor.amount);
            transfers.add(new Transfer(debtor.participant, creditor.participant, amount));
            creditor.amount = creditor.amount.subtract(amount);
            debtor.amount = debtor.amount.subtract(amount);
            if (creditor.amount.signum() == 0) creditors.remove(0);
            if (debtor.amount.signum() == 0) debtors.remove(0);
        }
        return transfers;
    }

    /**
     * Debts as they arose, person to person, netted both ways: what A owes B from the expenses B
     * paid for, minus what B owes A, minus what A already handed B. With several payers a share is
     * owed to each payer in proportion to what they paid. Rounded to {@code digits}; largest first.
     */
    public static List<Transfer> pairwise(List<ExpenseLine> expenses, List<Payment> payments,
                                          String currency, int digits) {
        Map<ParticipantRef, Map<ParticipantRef, BigDecimal>> debts = new HashMap<>();
        for (ExpenseLine expense : expenses) {
            if (!currency.equals(expense.currency())) continue;
            List<Amount> owed = effectiveOwed(expense);
            if (owed == null) continue;
            BigDecimal paidTotal = expense.payers().stream().map(Amount::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            for (Amount share : owed) {
                for (Amount payer : expense.payers()) {
                    if (share.participant().equals(payer.participant())) continue;
                    BigDecimal part = share.amount().multiply(payer.amount())
                            .divide(paidTotal, WORK_SCALE, RoundingMode.HALF_UP);
                    add(debts, share.participant(), payer.participant(), part);
                }
            }
        }
        for (Payment payment : payments) {
            if (!currency.equals(payment.currency()) || payment.from().equals(payment.to())) continue;
            // A paid B: A owes B that much less, which is B owing A that much.
            add(debts, payment.to(), payment.from(), payment.amount());
        }
        List<Transfer> transfers = new ArrayList<>();
        List<ParticipantRef> people = new ArrayList<>(debts.keySet());
        debts.values().forEach(row -> row.keySet().forEach(person -> {
            if (!people.contains(person)) people.add(person);
        }));
        people.sort(Comparator.comparing(ParticipantRef::key));
        for (int i = 0; i < people.size(); i++) {
            for (int j = i + 1; j < people.size(); j++) {
                ParticipantRef a = people.get(i);
                ParticipantRef b = people.get(j);
                BigDecimal net = debt(debts, a, b).subtract(debt(debts, b, a)).setScale(digits, RoundingMode.HALF_UP);
                if (net.signum() > 0) transfers.add(new Transfer(a, b, net));
                if (net.signum() < 0) transfers.add(new Transfer(b, a, net.negate()));
            }
        }
        transfers.sort(Comparator.comparing(Transfer::amount).reversed()
                .thenComparing(transfer -> transfer.from().key())
                .thenComparing(transfer -> transfer.to().key()));
        return transfers;
    }

    /** The owed lines with the unassigned rest added to the first payer; null when nobody paid. */
    private static List<Amount> effectiveOwed(ExpenseLine expense) {
        if (expense.payers() == null || expense.payers().isEmpty()) return null;
        BigDecimal paid = expense.payers().stream().map(Amount::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paid.signum() <= 0) return null;
        List<Amount> owed = new ArrayList<>(expense.owed() == null ? List.of() : expense.owed());
        BigDecimal assigned = owed.stream().map(Amount::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rest = paid.subtract(assigned);
        if (rest.signum() != 0) owed.add(new Amount(expense.payers().get(0).participant(), rest));
        return owed;
    }

    private static Balance balance(CurrencyLedger ledger, ParticipantRef participant) {
        return ledger.members().computeIfAbsent(participant, ignored -> new Balance());
    }

    private static void add(Map<ParticipantRef, Map<ParticipantRef, BigDecimal>> debts,
                            ParticipantRef debtor, ParticipantRef creditor, BigDecimal amount) {
        debts.computeIfAbsent(debtor, ignored -> new HashMap<>()).merge(creditor, amount, BigDecimal::add);
    }

    private static BigDecimal debt(Map<ParticipantRef, Map<ParticipantRef, BigDecimal>> debts,
                                   ParticipantRef debtor, ParticipantRef creditor) {
        return debts.getOrDefault(debtor, Map.of()).getOrDefault(creditor, BigDecimal.ZERO);
    }

    private static final class Entry {
        private final ParticipantRef participant;
        private BigDecimal amount;

        private Entry(ParticipantRef participant, BigDecimal amount) {
            this.participant = participant;
            this.amount = amount;
        }
    }
}
