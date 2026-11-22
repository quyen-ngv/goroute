package com.ds.goroute.service.expense;

import com.ds.goroute.service.expense.LedgerCalculator.Amount;
import com.ds.goroute.service.expense.LedgerCalculator.CurrencyLedger;
import com.ds.goroute.service.expense.LedgerCalculator.ExpenseLine;
import com.ds.goroute.service.expense.LedgerCalculator.Payment;
import com.ds.goroute.service.expense.LedgerCalculator.Transfer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerCalculatorTest {

    private final ParticipantRef an = ParticipantRef.user(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private final ParticipantRef binh = ParticipantRef.user(UUID.fromString("00000000-0000-0000-0000-00000000000b"));
    private final ParticipantRef chi = ParticipantRef.user(UUID.fromString("00000000-0000-0000-0000-00000000000c"));
    private final ParticipantRef guest = ParticipantRef.guest(UUID.fromString("00000000-0000-0000-0000-0000000000d0"));

    private static Amount amount(ParticipantRef who, String value) {
        return new Amount(who, new BigDecimal(value));
    }

    private static ExpenseLine vnd(List<Amount> payers, List<Amount> owed) {
        return new ExpenseLine("VND", payers, owed);
    }

    private static BigDecimal total(Map<ParticipantRef, BigDecimal> nets) {
        return nets.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("net = paid − owed + paid out − received, and every currency adds up to zero")
    void balancesAddUpToZero() {
        List<ExpenseLine> expenses = List.of(
                vnd(List.of(amount(an, "300000")), List.of(amount(an, "100000"), amount(binh, "100000"), amount(chi, "100000"))),
                vnd(List.of(amount(binh, "90000")), List.of(amount(an, "30000"), amount(binh, "30000"), amount(guest, "30000"))));
        List<Payment> payments = List.of(new Payment(chi, an, new BigDecimal("50000"), "VND"));

        CurrencyLedger ledger = LedgerCalculator.balances(expenses, payments).get("VND");

        Map<ParticipantRef, BigDecimal> nets = ledger.nets();
        assertThat(nets.get(an)).isEqualByComparingTo("120000");    // 300000 − 130000 − 50000 received
        assertThat(nets.get(binh)).isEqualByComparingTo("-40000");  // 90000 − 130000
        assertThat(nets.get(chi)).isEqualByComparingTo("-50000");   // −100000 + 50000 paid out
        assertThat(nets.get(guest)).isEqualByComparingTo("-30000");
        assertThat(total(nets)).isEqualByComparingTo("0");
        assertThat(ledger.members().get(an).settledIn()).isEqualByComparingTo("50000");
        assertThat(ledger.members().get(chi).settledOut()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("several payers each count what they paid")
    void severalPayers() {
        List<ExpenseLine> expenses = List.of(vnd(
                List.of(amount(an, "200000"), amount(binh, "100000")),
                List.of(amount(an, "100000"), amount(binh, "100000"), amount(chi, "100000"))));

        Map<ParticipantRef, BigDecimal> nets = LedgerCalculator.balances(expenses, List.of()).get("VND").nets();

        assertThat(nets.get(an)).isEqualByComparingTo("100000");
        assertThat(nets.get(binh)).isEqualByComparingTo("0");
        assertThat(nets.get(chi)).isEqualByComparingTo("-100000");
    }

    @Test
    @DisplayName("currencies are kept apart, never converted")
    void currenciesStayApart() {
        List<ExpenseLine> expenses = List.of(
                vnd(List.of(amount(an, "100000")), List.of(amount(an, "50000"), amount(binh, "50000"))),
                new ExpenseLine("JPY", List.of(amount(binh, "2000")), List.of(amount(an, "1000"), amount(binh, "1000"))));

        Map<String, CurrencyLedger> ledgers = LedgerCalculator.balances(expenses, List.of());

        assertThat(ledgers).containsOnlyKeys("JPY", "VND");
        assertThat(ledgers.get("VND").nets().get(binh)).isEqualByComparingTo("-50000");
        assertThat(ledgers.get("JPY").nets().get(an)).isEqualByComparingTo("-1000");
    }

    @Test
    @DisplayName("a share that names nobody stays with the first payer, so the sum is still zero")
    void unassignedRestStaysWithThePayer() {
        List<ExpenseLine> expenses = List.of(vnd(List.of(amount(an, "100000")), List.of(amount(binh, "60000"))));

        Map<ParticipantRef, BigDecimal> nets = LedgerCalculator.balances(expenses, List.of()).get("VND").nets();

        assertThat(nets.get(an)).isEqualByComparingTo("60000");
        assertThat(total(nets)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("an expense nobody paid is left out")
    void expenseWithoutPayerIsIgnored() {
        assertThat(LedgerCalculator.balances(List.of(vnd(List.of(), List.of(amount(an, "100")))), List.of())).isEmpty();
    }

    @Test
    @DisplayName("simplified: the largest debtor pays the largest creditor")
    void simplifyMatchesLargestFirst() {
        Map<ParticipantRef, BigDecimal> nets = new LinkedHashMap<>();
        nets.put(an, new BigDecimal("150000"));
        nets.put(binh, new BigDecimal("-100000"));
        nets.put(chi, new BigDecimal("-50000"));

        List<Transfer> transfers = LedgerCalculator.simplify(nets);

        assertThat(transfers).containsExactly(
                new Transfer(binh, an, new BigDecimal("100000")),
                new Transfer(chi, an, new BigDecimal("50000")));
    }

    @Test
    @DisplayName("simplified: a chain A→B→C collapses into one transfer")
    void simplifyCollapsesAChain() {
        // An paid for Binh, Binh paid the same for Chi: Chi only needs to pay An.
        List<ExpenseLine> expenses = List.of(
                vnd(List.of(amount(an, "100000")), List.of(amount(binh, "100000"))),
                vnd(List.of(amount(binh, "100000")), List.of(amount(chi, "100000"))));
        Map<ParticipantRef, BigDecimal> nets = LedgerCalculator.balances(expenses, List.of()).get("VND").nets();

        assertThat(LedgerCalculator.simplify(nets)).containsExactly(new Transfer(chi, an, new BigDecimal("100000")));
        assertThat(LedgerCalculator.pairwise(expenses, List.of(), "VND", 0)).hasSize(2);
    }

    @Test
    @DisplayName("simplified: never more than n − 1 transfers, and they clear every balance")
    void simplifyIsMinimalAndComplete() {
        Random random = new Random(42);
        for (int round = 0; round < 200; round++) {
            int people = 2 + random.nextInt(8);
            List<ParticipantRef> refs = new ArrayList<>();
            for (int i = 0; i < people; i++) refs.add(ParticipantRef.user(new UUID(round, i)));
            List<ExpenseLine> expenses = new ArrayList<>();
            for (int e = 0; e < 1 + random.nextInt(6); e++) {
                ParticipantRef payer = refs.get(random.nextInt(people));
                List<Amount> owed = new ArrayList<>();
                BigDecimal paid = BigDecimal.ZERO;
                for (ParticipantRef ref : refs) {
                    if (random.nextBoolean()) {
                        BigDecimal share = BigDecimal.valueOf(1000L * (1 + random.nextInt(50)));
                        owed.add(new Amount(ref, share));
                        paid = paid.add(share);
                    }
                }
                if (paid.signum() > 0) expenses.add(vnd(List.of(new Amount(payer, paid)), owed));
            }
            if (expenses.isEmpty()) continue;
            Map<ParticipantRef, BigDecimal> nets = LedgerCalculator.balances(expenses, List.of()).get("VND").nets();
            assertThat(total(nets)).isEqualByComparingTo("0");

            List<Transfer> transfers = LedgerCalculator.simplify(nets);
            long nonZero = nets.values().stream().filter(net -> net.signum() != 0).count();
            assertThat(transfers.size()).isLessThanOrEqualTo((int) Math.max(0, nonZero - 1));

            Map<ParticipantRef, BigDecimal> after = new LinkedHashMap<>(nets);
            for (Transfer transfer : transfers) {
                assertThat(transfer.amount().signum()).isPositive();
                after.merge(transfer.from(), transfer.amount(), BigDecimal::add);
                after.merge(transfer.to(), transfer.amount().negate(), BigDecimal::add);
            }
            assertThat(after.values()).allSatisfy(net -> assertThat(net).isEqualByComparingTo("0"));
        }
    }

    @Test
    @DisplayName("pairwise: debts between two people are netted both ways, payments taken off")
    void pairwiseNetsBothWays() {
        List<ExpenseLine> expenses = List.of(
                vnd(List.of(amount(an, "100000")), List.of(amount(an, "50000"), amount(binh, "50000"))),
                vnd(List.of(amount(binh, "60000")), List.of(amount(an, "30000"), amount(binh, "30000"))));
        List<Payment> payments = List.of(new Payment(binh, an, new BigDecimal("5000"), "VND"));

        List<Transfer> transfers = LedgerCalculator.pairwise(expenses, payments, "VND", 0);

        // Binh owes An 50000, An owes Binh 30000, Binh already paid 5000: Binh still owes 15000.
        assertThat(transfers).containsExactly(new Transfer(binh, an, new BigDecimal("15000")));
    }

    @Test
    @DisplayName("pairwise: with several payers a share is owed to each in proportion")
    void pairwiseSplitsAShareBetweenPayers() {
        List<ExpenseLine> expenses = List.of(vnd(
                List.of(amount(an, "200000"), amount(binh, "100000")),
                List.of(amount(chi, "300000"))));

        List<Transfer> transfers = LedgerCalculator.pairwise(expenses, List.of(), "VND", 0);

        assertThat(transfers).containsExactly(
                new Transfer(chi, an, new BigDecimal("200000")),
                new Transfer(chi, binh, new BigDecimal("100000")));
    }

    @Test
    @DisplayName("a settled debt leaves nothing to transfer")
    void settledDebtLeavesNothing() {
        List<ExpenseLine> expenses = List.of(
                vnd(List.of(amount(an, "100000")), List.of(amount(an, "50000"), amount(binh, "50000"))));
        List<Payment> payments = List.of(new Payment(binh, an, new BigDecimal("50000"), "VND"));

        Map<ParticipantRef, BigDecimal> nets = LedgerCalculator.balances(expenses, payments).get("VND").nets();

        assertThat(LedgerCalculator.simplify(nets)).isEmpty();
        assertThat(LedgerCalculator.pairwise(expenses, payments, "VND", 0)).isEmpty();
    }
}
