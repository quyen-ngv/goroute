package com.ds.goroute.service.expense;

import com.ds.goroute.dto.request.ExpensePayerRequest;
import com.ds.goroute.dto.request.ExpenseSplitRequest;
import com.ds.goroute.service.expense.ExpenseComposition.PayerLine;
import com.ds.goroute.service.expense.ExpenseComposition.SplitLine;
import com.ds.goroute.type.SplitMethod;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.ds.goroute.service.expense.ExpenseComposition.invalid;

/**
 * Reads the payer and split parts of a create/update request (trip or personal) into an
 * {@link ExpenseComposition}. Who may take part is the caller's {@link Resolver}; the arithmetic
 * is {@link SplitCalculator}'s. Pure apart from the resolver.
 */
public final class ExpenseComposer {

    /** Methods whose {@code shareInput} is stored with the share. */
    private static final Set<SplitMethod> STORES_SHARE_INPUT =
            EnumSet.of(SplitMethod.PERCENT, SplitMethod.SHARES, SplitMethod.ADJUSTMENT);

    private ExpenseComposer() {
    }

    /** Who may be named, and how. */
    public interface Resolver {
        /**
         * The participant these request fields name, checked to be allowed on this expense.
         *
         * @param guestName a legacy share may name a guest only by name
         * @return null only for a legacy name-only share the resolver accepts as such
         */
        ParticipantRef resolve(UUID userId, UUID guestMemberId, String guestName, boolean legacy);

        /** The name to keep on a guest share; null for a user. */
        String guestName(ParticipantRef participant);
    }

    /** The share lines, and the method to store with them (null for an old build's request). */
    public record Shares(SplitMethod storedMethod, List<SplitLine> splits) {
    }

    /**
     * Payers from the new {@code payers} list, else {@code legacyPayer} for the whole amount.
     */
    public static List<PayerLine> payers(List<ExpensePayerRequest> requested, ParticipantRef legacyPayer,
                                         BigDecimal amount, Resolver resolver) {
        if (requested == null || requested.isEmpty()) {
            if (legacyPayer == null) throw invalid("Choose who paid");
            return List.of(new PayerLine(legacyPayer, amount));
        }
        List<PayerLine> lines = new ArrayList<>();
        for (ExpensePayerRequest payer : requested) {
            ParticipantRef participant = resolver.resolve(payer.getUserId(), payer.getGuestMemberId(), null, false);
            if (participant == null) throw invalid("Each payer must be a user or a guest");
            lines.add(new PayerLine(participant, payer.getAmount()));
        }
        return ExpenseComposition.checkPayers(lines, amount);
    }

    /**
     * The shares for {@code method}. Without a method (an old build) the split amounts are taken
     * as sent, checked to add up to the amount; no splits at all is accepted as before.
     */
    public static Shares shares(SplitMethod method, BigDecimal amount, String currency,
                                List<ExpenseSplitRequest> splits, Resolver resolver) {
        boolean legacy = method == null;
        if (splits == null || splits.isEmpty()) {
            if (legacy) return new Shares(null, List.of());
            throw invalid("Choose who shares this expense");
        }
        List<ParticipantRef> participants = new ArrayList<>();
        List<SplitCalculator.ShareInput> inputs = new ArrayList<>();
        for (ExpenseSplitRequest split : splits) {
            ParticipantRef participant = resolver.resolve(split.getUserId(), split.getGuestMemberId(),
                    split.getGuestName(), legacy);
            if (participant == null && !legacy) throw invalid("Each share must name a user or a guest");
            participants.add(participant);
            inputs.add(new SplitCalculator.ShareInput(split.getAmount(), split.getShareInput()));
        }
        List<BigDecimal> amounts = SplitCalculator.split(legacy ? SplitMethod.EXACT : method, amount, currency, inputs);
        List<SplitLine> lines = new ArrayList<>();
        for (int i = 0; i < splits.size(); i++) {
            ParticipantRef participant = participants.get(i);
            String guestName = participant == null ? trimmed(splits.get(i).getGuestName())
                    : resolver.guestName(participant);
            BigDecimal shareInput = !legacy && STORES_SHARE_INPUT.contains(method) ? inputs.get(i).shareInput() : null;
            lines.add(new SplitLine(participant, guestName, amounts.get(i), shareInput));
        }
        ExpenseComposition.checkDistinct(lines);
        return new Shares(method, lines);
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }
}
