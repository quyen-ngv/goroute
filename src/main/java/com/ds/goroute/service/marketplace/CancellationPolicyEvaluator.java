package com.ds.goroute.service.marketplace;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the free-form {@code cancellationPolicy} map stored on rate plans / activity packages
 * (and frozen into every booking snapshot) into a concrete outcome for "cancel now".
 *
 * <p>Recognised keys, all optional and written by the partner console:
 * <ul>
 *   <li>{@code type} — FLEXIBLE | MODERATE | STRICT | NON_REFUNDABLE | CUSTOM.</li>
 *   <li>{@code freeCancellationHours} — hours before service start until which cancellation is free.
 *       When absent, the type supplies a default (24h / 120h / 168h); NON_REFUNDABLE is never free.</li>
 *   <li>{@code penaltyType} + {@code penaltyValue} — PERCENT | FIXED | FIRST_NIGHT | FULL_STAY, or the
 *       shorthand {@code penaltyPercent}. When absent the penalty is the full amount.</li>
 * </ul>
 *
 * <p>The evaluator is deliberately pure: no clock, no repository. Callers pass the service start in
 * the property/slot timezone and "now" in the same zone. Money is not collected yet, so the penalty is
 * informational (shown to the guest before they confirm, recorded on the cancellation) — but the rule
 * lives here so a payment provider can charge exactly this number later without re-deriving it.
 */
public final class CancellationPolicyEvaluator {
    public static final String TYPE_FLEXIBLE = "FLEXIBLE";
    public static final String TYPE_MODERATE = "MODERATE";
    public static final String TYPE_STRICT = "STRICT";
    public static final String TYPE_NON_REFUNDABLE = "NON_REFUNDABLE";
    public static final String TYPE_CUSTOM = "CUSTOM";

    private CancellationPolicyEvaluator() {}

    /** Result of evaluating a policy at a point in time. Amounts are in the booking currency. */
    public record Outcome(String policyType, boolean free, LocalDateTime freeUntil, BigDecimal penaltyAmount,
                          String penaltyRule) {}

    /**
     * @param policy        the snapshot map (may be null/empty → treated as FLEXIBLE, 24h)
     * @param serviceStart  check-in date-time or slot start, in the service timezone
     * @param now           current time in the same zone
     * @param totalAmount   amount the guest agreed to pay
     * @param firstUnitAmount price of the first night / first unit, used by FIRST_NIGHT; falls back to total
     */
    public static Outcome evaluate(Map<String, Object> policy, LocalDateTime serviceStart, LocalDateTime now,
                                   BigDecimal totalAmount, BigDecimal firstUnitAmount) {
        Map<String, Object> p = policy == null ? Map.of() : policy;
        String type = normalizeType(p.get("type"));
        BigDecimal total = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        if (TYPE_NON_REFUNDABLE.equals(type)) {
            return new Outcome(type, false, null, scale(total), "NON_REFUNDABLE");
        }
        Long hours = longValue(p.get("freeCancellationHours"));
        if (hours == null) hours = defaultFreeHours(type);
        LocalDateTime freeUntil = serviceStart == null ? null : serviceStart.minus(Duration.ofHours(hours));
        boolean free = freeUntil != null && now != null && !now.isAfter(freeUntil);
        if (free) {
            return new Outcome(type, true, freeUntil, BigDecimal.ZERO, "FREE_UNTIL_" + hours + "H");
        }
        return penalty(type, freeUntil, p, total, firstUnitAmount);
    }

    private static Outcome penalty(String type, LocalDateTime freeUntil, Map<String, Object> p, BigDecimal total,
                                   BigDecimal firstUnit) {
        BigDecimal percentShorthand = decimal(p.get("penaltyPercent"));
        String penaltyType = p.get("penaltyType") == null ? null : String.valueOf(p.get("penaltyType")).trim().toUpperCase(Locale.ROOT);
        BigDecimal value = decimal(p.get("penaltyValue"));
        if (penaltyType == null && percentShorthand != null) {
            penaltyType = "PERCENT";
            value = percentShorthand;
        }
        if (penaltyType == null) {
            return new Outcome(type, false, freeUntil, scale(total), "FULL_AMOUNT");
        }
        return switch (penaltyType) {
            case "PERCENT" -> {
                BigDecimal pct = clampPercent(value);
                yield new Outcome(type, false, freeUntil,
                        scale(total.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)),
                        "PERCENT_" + pct.stripTrailingZeros().toPlainString());
            }
            case "FIXED" -> {
                BigDecimal fixed = value == null ? total : value.min(total).max(BigDecimal.ZERO);
                yield new Outcome(type, false, freeUntil, scale(fixed), "FIXED");
            }
            case "FIRST_NIGHT", "FIRST_UNIT" -> {
                BigDecimal first = firstUnit == null ? total : firstUnit.min(total).max(BigDecimal.ZERO);
                yield new Outcome(type, false, freeUntil, scale(first), "FIRST_NIGHT");
            }
            default -> new Outcome(type, false, freeUntil, scale(total), "FULL_AMOUNT");
        };
    }

    static long defaultFreeHours(String type) {
        return switch (type) {
            case TYPE_MODERATE -> 5 * 24L;
            case TYPE_STRICT -> 7 * 24L;
            case TYPE_CUSTOM -> 0L;
            default -> 24L;
        };
    }

    private static String normalizeType(Object raw) {
        if (raw == null) return TYPE_FLEXIBLE;
        String type = String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case TYPE_MODERATE, TYPE_STRICT, TYPE_NON_REFUNDABLE, TYPE_CUSTOM -> type;
            default -> TYPE_FLEXIBLE;
        };
    }

    private static BigDecimal clampPercent(BigDecimal value) {
        if (value == null) return BigDecimal.valueOf(100);
        return value.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
    }

    private static Long longValue(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(raw).trim()); } catch (NumberFormatException ex) { return null; }
    }

    private static BigDecimal decimal(Object raw) {
        if (raw == null) return null;
        if (raw instanceof BigDecimal b) return b;
        if (raw instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(raw).trim()); } catch (NumberFormatException ex) { return null; }
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
