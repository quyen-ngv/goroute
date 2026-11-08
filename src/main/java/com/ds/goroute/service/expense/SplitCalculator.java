package com.ds.goroute.service.expense;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.type.SplitMethod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;

/**
 * Turns "how do you want to split this" into the amount each person bears (epic 05 §2.3).
 *
 * <p>Pure: no I/O, no Spring. Every result adds up to exactly the total. Amounts are rounded to the
 * currency's minor unit ({@link Currency#getDefaultFractionDigits()}, capped at the two decimals the
 * database stores); the units a proportional split cannot place evenly are handed out one at a time
 * from the first person on, so the same input always yields the same split.
 */
public final class SplitCalculator {

    /** {@code expenses.amount} and {@code expense_splits.amount} are DECIMAL(15,2). */
    private static final int STORED_DIGITS = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    /** Percentages typed as 33.33 three times add up to 99.99; that is still "all of it". */
    private static final BigDecimal PERCENT_TOLERANCE = new BigDecimal("0.01");

    private SplitCalculator() {
    }

    /** One person's line of the request: an amount (EXACT) and/or a share input. */
    public record ShareInput(BigDecimal amount, BigDecimal shareInput) {
    }

    /**
     * The minor-unit digits to compute in: the currency's, or more when the total itself carries
     * more decimals (a VND amount stored as 100.50), never more than the two the database keeps.
     */
    public static int fractionDigits(String currency, BigDecimal total) {
        int digits = STORED_DIGITS;
        try {
            int currencyDigits = Currency.getInstance(currency).getDefaultFractionDigits();
            digits = currencyDigits < 0 ? STORED_DIGITS : currencyDigits;
        } catch (RuntimeException ignored) {
            // Unknown or null code: keep two decimals, as the column does.
        }
        int scale = total == null ? 0 : Math.max(0, total.stripTrailingZeros().scale());
        return Math.min(STORED_DIGITS, Math.max(digits, scale));
    }

    /**
     * The amount each line bears, in request order.
     *
     * @throws BusinessException INVALID_PARAMETERS when the inputs cannot make up the total
     */
    public static List<BigDecimal> split(SplitMethod method, BigDecimal total, String currency,
                                         List<ShareInput> shares) {
        if (total == null || total.signum() <= 0) throw invalid("The amount must be positive");
        if (shares == null || shares.isEmpty()) throw invalid("Choose who shares this expense");
        int digits = fractionDigits(currency, total);
        return switch (method) {
            case EQUAL -> distribute(total, Collections.nCopies(shares.size(), BigDecimal.ONE), digits);
            case EXACT -> exact(total, shares, digits);
            case PERCENT -> percent(total, shares, digits);
            case SHARES -> shares(total, shares, digits);
            case ADJUSTMENT -> adjustment(total, shares, digits);
        };
    }

    /**
     * {@code total} split in proportion to {@code weights} (each ≥ 0, at least one above 0), in
     * the currency's minor unit; used to keep an expense's proportions when only its amount changes.
     */
    public static List<BigDecimal> proportional(BigDecimal total, String currency, List<BigDecimal> weights) {
        if (total == null || total.signum() <= 0) throw invalid("The amount must be positive");
        for (BigDecimal weight : weights) {
            if (weight == null || weight.signum() < 0) throw invalid("A share cannot be negative");
        }
        return distribute(total, weights, fractionDigits(currency, total));
    }

    /**
     * Whether these amounts are the even split of the total, with the remainder either spread
     * from the first person (this class) or put on the last one (rows written before V268).
     */
    public static boolean isEvenSplit(BigDecimal total, String currency, List<BigDecimal> amounts) {
        if (total == null || amounts == null || amounts.isEmpty()) return false;
        if (amounts.stream().anyMatch(java.util.Objects::isNull)) return false;
        int digits = fractionDigits(currency, total);
        int count = amounts.size();
        List<BigDecimal> actual = sorted(amounts);
        if (sameValues(actual, sorted(distribute(total, Collections.nCopies(count, BigDecimal.ONE), digits)))) {
            return true;
        }
        BigDecimal each = total.divide(BigDecimal.valueOf(count), digits, RoundingMode.DOWN);
        List<BigDecimal> lastTakesRest = new ArrayList<>(Collections.nCopies(count - 1, each));
        lastTakesRest.add(total.subtract(each.multiply(BigDecimal.valueOf(count - 1L))));
        return sameValues(actual, sorted(lastTakesRest));
    }

    /**
     * Splits {@code total} in proportion to {@code weights}: each line gets its proportional
     * share rounded down to the minor unit, then the units left over go one each to the lines
     * with a positive weight, first line first.
     */
    static List<BigDecimal> distribute(BigDecimal total, List<BigDecimal> weights, int digits) {
        BigDecimal units = total.movePointRight(digits).setScale(0, RoundingMode.DOWN);
        BigDecimal weightSum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weightSum.signum() <= 0) throw invalid("The shares must be more than zero");
        List<BigDecimal> parts = new ArrayList<>(weights.size());
        BigDecimal placed = BigDecimal.ZERO;
        for (BigDecimal weight : weights) {
            BigDecimal part = units.multiply(weight).divide(weightSum, 0, RoundingMode.DOWN);
            parts.add(part);
            placed = placed.add(part);
        }
        int left = units.subtract(placed).intValueExact();
        List<Integer> eligible = new ArrayList<>();
        for (int i = 0; i < weights.size(); i++) {
            if (weights.get(i).signum() > 0) eligible.add(i);
        }
        for (int given = 0; given < left; given++) {
            int index = eligible.get(given % eligible.size());
            parts.set(index, parts.get(index).add(BigDecimal.ONE));
        }
        // Units that could not be expressed (a total with more decimals than digits) stay on the
        // first line, so the parts still add up to the exact total.
        BigDecimal unexpressed = total.subtract(units.movePointLeft(digits));
        List<BigDecimal> amounts = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            BigDecimal amount = parts.get(i).movePointLeft(digits).setScale(digits, RoundingMode.UNNECESSARY);
            if (i == eligible.get(0) && unexpressed.signum() != 0) amount = amount.add(unexpressed);
            amounts.add(amount);
        }
        return amounts;
    }

    /**
     * Exact amounts must add up to the total. Old app builds sent "amount / n" unrounded (plan
     * §1 bug 1): a sum within one minor unit per line of the total is that rounding, and is
     * re-spread in proportion to the amounts sent rather than refused.
     */
    private static List<BigDecimal> exact(BigDecimal total, List<ShareInput> shares, int digits) {
        List<BigDecimal> amounts = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        boolean onScale = true;
        for (ShareInput share : shares) {
            BigDecimal amount = share.amount();
            if (amount == null || amount.signum() < 0) throw invalid("Each share needs an amount of 0 or more");
            amounts.add(amount);
            sum = sum.add(amount);
            onScale &= amount.stripTrailingZeros().scale() <= digits;
        }
        if (sum.compareTo(total) == 0 && onScale) return amounts;
        BigDecimal tolerance = BigDecimal.ONE.movePointLeft(digits).multiply(BigDecimal.valueOf(shares.size()));
        if (sum.signum() > 0 && sum.subtract(total).abs().compareTo(tolerance) <= 0) {
            return distribute(total, amounts, digits);
        }
        throw invalid("The shares must add up to the amount");
    }

    private static List<BigDecimal> percent(BigDecimal total, List<ShareInput> shares, int digits) {
        List<BigDecimal> weights = new ArrayList<>();
        for (ShareInput share : shares) {
            BigDecimal value = share.shareInput();
            if (value == null || value.signum() < 0) throw invalid("Each percentage must be 0 or more");
            weights.add(value);
        }
        BigDecimal sum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.subtract(HUNDRED).abs().compareTo(PERCENT_TOLERANCE) > 0) {
            throw invalid("The percentages must add up to 100");
        }
        return distribute(total, weights, digits);
    }

    private static List<BigDecimal> shares(BigDecimal total, List<ShareInput> shares, int digits) {
        List<BigDecimal> weights = new ArrayList<>();
        for (ShareInput share : shares) {
            BigDecimal value = share.shareInput();
            if (value == null || value.signum() <= 0) throw invalid("Each person needs more than zero shares");
            weights.add(value);
        }
        return distribute(total, weights, digits);
    }

    /** Each adjustment is added to (or taken off) that person; what is left is split evenly. */
    private static List<BigDecimal> adjustment(BigDecimal total, List<ShareInput> shares, int digits) {
        List<BigDecimal> adjustments = new ArrayList<>();
        BigDecimal adjusted = BigDecimal.ZERO;
        for (ShareInput share : shares) {
            BigDecimal value = share.shareInput() == null ? BigDecimal.ZERO
                    : share.shareInput().setScale(digits, RoundingMode.HALF_UP);
            adjustments.add(value);
            adjusted = adjusted.add(value);
        }
        BigDecimal rest = total.subtract(adjusted);
        if (rest.signum() < 0) throw invalid("The adjustments are more than the amount");
        List<BigDecimal> even = rest.signum() == 0
                ? new ArrayList<>(Collections.nCopies(shares.size(), BigDecimal.ZERO.setScale(digits)))
                : distribute(rest, Collections.nCopies(shares.size(), BigDecimal.ONE), digits);
        List<BigDecimal> amounts = new ArrayList<>();
        for (int i = 0; i < shares.size(); i++) {
            BigDecimal amount = even.get(i).add(adjustments.get(i));
            if (amount.signum() < 0) throw invalid("An adjustment cannot leave a share below zero");
            amounts.add(amount);
        }
        return amounts;
    }

    private static List<BigDecimal> sorted(List<BigDecimal> values) {
        List<BigDecimal> copy = new ArrayList<>(values);
        copy.sort(BigDecimal::compareTo);
        return copy;
    }

    private static boolean sameValues(List<BigDecimal> left, List<BigDecimal> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (left.get(i).compareTo(right.get(i)) != 0) return false;
        }
        return true;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorConstant.INVALID_PARAMETERS, message);
    }
}
