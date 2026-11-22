package com.ds.goroute.service.expense;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.type.SplitMethod;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Who paid and who bears what for one expense, ready to be written (epic 05 §8.2).
 *
 * @param method the method to store; null for rows the legacy request shape wrote
 * @param payers everyone who paid, amounts adding up to the expense amount
 * @param splits the final share of each person, adding up to the amount (or empty)
 */
public record ExpenseComposition(SplitMethod method, List<PayerLine> payers, List<SplitLine> splits) {

    public record PayerLine(ParticipantRef participant, BigDecimal amount) {
    }

    /**
     * @param participant null only for a legacy share that names a guest by name alone
     * @param guestName   kept on guest shares, as {@code expense_splits.guest_name}
     */
    public record SplitLine(ParticipantRef participant, String guestName, BigDecimal amount, BigDecimal shareInput) {
    }

    /** The person who paid the most, first on a tie; what {@code expenses.paid_by} shows old builds. */
    public ParticipantRef primaryPayer() {
        PayerLine best = null;
        for (PayerLine payer : payers) {
            if (best == null || payer.amount().compareTo(best.amount()) > 0) best = payer;
        }
        return best == null ? null : best.participant();
    }

    /**
     * Validates requested payers: each person once, no negative amount, at least one who paid
     * something, and the amounts adding up to {@code total}. Payers with 0 are dropped.
     */
    public static List<PayerLine> checkPayers(List<PayerLine> requested, BigDecimal total) {
        if (requested == null || requested.isEmpty()) throw invalid("Choose who paid");
        Set<ParticipantRef> seen = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        List<PayerLine> payers = new ArrayList<>();
        for (PayerLine payer : requested) {
            if (payer.participant() == null) throw invalid("Each payer must be a user or a guest");
            if (!seen.add(payer.participant())) throw invalid("Each person can pay once");
            if (payer.amount() == null || payer.amount().signum() < 0) throw invalid("A payer amount cannot be negative");
            sum = sum.add(payer.amount());
            if (payer.amount().signum() > 0) payers.add(payer);
        }
        if (payers.isEmpty()) throw invalid("Someone has to have paid");
        if (sum.compareTo(total) != 0) throw invalid("What the payers paid must add up to the amount");
        return payers;
    }

    /** Each identified person at most once among the shares. */
    public static void checkDistinct(List<SplitLine> splits) {
        Set<ParticipantRef> seen = new HashSet<>();
        for (SplitLine split : splits) {
            if (split.participant() != null && !seen.add(split.participant())) {
                throw invalid("Each person can be in a split once");
            }
        }
    }

    static BusinessException invalid(String message) {
        return new BusinessException(ErrorConstant.INVALID_PARAMETERS, message);
    }
}
