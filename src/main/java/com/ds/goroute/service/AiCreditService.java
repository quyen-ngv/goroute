package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.AiCreditStatus;
import com.ds.goroute.dto.response.AiCreditTransactionPageResponse;
import com.ds.goroute.dto.response.AiCreditTransactionResponse;
import com.ds.goroute.dto.response.AiCreditWalletResponse;
import com.ds.goroute.entity.AiApiCall;
import com.ds.goroute.entity.AiCreditPlan;
import com.ds.goroute.entity.AiCreditTransaction;
import com.ds.goroute.entity.AiCreditWallet;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.AiCreditRepository;
import com.ds.goroute.type.AiCreditFeature;
import com.ds.goroute.type.AiCreditTransactionType;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.UserRealtimeEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * AI credits: the one limit on every AI feature a user triggers (Pengoo, AI trip, receipt scan,
 * social video). Each model call is charged its real cost x AI_CREDIT.MARKUP as it is logged.
 *
 * <ul>
 *   <li>Two buckets. The plan bucket is reset to the plan's allowance every period (FREE monthly,
 *       each PRO plan on its own cycle), unused plan credits do not carry over, and debt is paid
 *       from the refill. Bought credits never expire. Charges take the plan bucket first.</li>
 *   <li>Starting a task needs AI_CREDIT.MIN_BALANCE_* for the feature. A running task may finish
 *       below zero, down to minus AI_CREDIT.NEGATIVE_FLOOR; past that the worker is told to stop.
 *       Beta users are charged like everyone but never refused or stopped.</li>
 *   <li>A task that fails on our side is refunded in full; a task stopped for credit is not.</li>
 *   <li>Every write locks the wallet row and carries a unique reference key, so a retried report
 *       never charges twice. Refills happen lazily, on the first read or write of a new period.</li>
 * </ul>
 *
 * <p>Every method joins the caller's transaction instead of opening its own: receipt scan locks
 * the wallet and then logs its model call in the same thread, and a REQUIRES_NEW charge there
 * would wait on its own caller's lock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCreditService {
    static final String FREE_PLAN = "FREE";
    private static final String PRO_TIER = "PRO";
    private static final String DEFAULT_PRO_PLAN = "PRO_MONTHLY";
    private static final int SCALE = 4;
    private static final int MAX_PAGE_SIZE = 100;

    private final AiCreditRepository repository;
    private final BusinessConfigService config;
    private final BetaAccessService betaAccess;
    private final UserRealtimePublisher realtime;

    /** Replaced in tests. */
    Clock clock = Clock.systemDefaultZone();

    @Transactional
    public AiCreditWalletResponse wallet(UUID userId) {
        AiCreditWallet wallet = openWallet(userId);
        return toResponse(userId, wallet);
    }

    @Transactional(readOnly = true)
    public AiCreditTransactionPageResponse transactions(UUID userId, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        return AiCreditTransactionPageResponse.builder()
                .items(repository.findTransactions(userId, safeSize, safePage * safeSize).stream()
                        .map(AiCreditTransactionResponse::from).toList())
                .total(repository.countTransactions(userId))
                .page(safePage)
                .size(safeSize)
                .build();
    }

    /** Whether the user may start these features now (their minimums add up); beta always may. */
    @Transactional
    public boolean canStart(UUID userId, AiCreditFeature... features) {
        if (betaAccess.isBetaUser(userId)) return true;
        return openWallet(userId).balance().compareTo(BigDecimal.valueOf(required(features))) >= 0;
    }

    /** Refuses with {@link ErrorConstant#AI_CREDIT_INSUFFICIENT} (HTTP 402) when {@link #canStart} is false. */
    @Transactional
    public void ensureCanStart(UUID userId, AiCreditFeature... features) {
        if (betaAccess.isBetaUser(userId)) return;
        BigDecimal balance = openWallet(userId).balance();
        int required = required(features);
        if (balance.compareTo(BigDecimal.valueOf(required)) >= 0) return;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("feature", features.length == 0 ? null : features[0].name());
        data.put("balance", balance.setScale(0, RoundingMode.FLOOR));
        data.put("required", required);
        throw new BusinessException(ErrorConstant.AI_CREDIT_INSUFFICIENT, data);
    }

    /**
     * Charges one logged model call of a billed feature. Returns the balance after it and whether
     * the task must stop, or null when the call is not billed (system feature, no user).
     */
    @Transactional
    public AiCreditStatus charge(AiApiCall call) {
        AiCreditFeature feature = AiCreditFeature.fromLogFeature(call.getFeature());
        if (feature == null || call.getUserId() == null) return null;
        UUID userId = call.getUserId();
        AiCreditWallet wallet = openWallet(userId);
        BigDecimal credits = credits(call);
        String key = "ai_call:" + (call.getCorrelationId() != null
                ? call.getFeature() + ":" + call.getOperation() + ":" + call.getCorrelationId()
                : call.getId());
        if (credits.signum() > 0 && !repository.hasReference(key)) {
            Split split = split(wallet, credits);
            record(wallet, AiCreditTransaction.builder()
                    .transactionType(AiCreditTransactionType.CHARGE.name())
                    .planAmount(split.plan().negate())
                    .purchasedAmount(split.purchased().negate())
                    .referenceKey(key)
                    .feature(feature.name())
                    .traceId(call.getTraceId())
                    .aiApiCallId(call.getId())
                    .costUsd(call.getCostUsd())
                    .build());
            wallet.setLifetimeSpent(wallet.getLifetimeSpent().add(credits));
            repository.updateWallet(wallet);
        }
        return status(userId, wallet);
    }

    /** Gives back every charge of a task that failed on our side. Safe to call twice. */
    @Transactional
    public void refundTask(UUID userId, String traceId, String reason) {
        if (userId == null || traceId == null) return;
        AiCreditWallet wallet = openWallet(userId);
        BigDecimal refunded = BigDecimal.ZERO;
        for (AiCreditTransaction charge : repository.findOpenCharges(userId, traceId)) {
            record(wallet, AiCreditTransaction.builder()
                    .transactionType(AiCreditTransactionType.REFUND.name())
                    .planAmount(charge.getPlanAmount().negate())
                    .purchasedAmount(charge.getPurchasedAmount().negate())
                    .referenceKey("refund:" + charge.getId())
                    .feature(charge.getFeature())
                    .traceId(traceId)
                    .reversesTransactionId(charge.getId())
                    .note(truncate(reason, 500))
                    .build());
            refunded = refunded.add(charge.getAmount().negate());
        }
        if (refunded.signum() > 0) {
            wallet.setLifetimeSpent(wallet.getLifetimeSpent().subtract(refunded).max(BigDecimal.ZERO));
            repository.updateWallet(wallet);
            log.info("Refunded {} credits of task {} to user {}", refunded, traceId, userId);
        }
    }

    /** An operator's correction: credits added go to the bought bucket, removals follow charge order. */
    @Transactional
    public AiCreditWalletResponse adjust(UUID userId, BigDecimal amount, String reason, UUID operatorId) {
        AiCreditWallet wallet = openWallet(userId);
        BigDecimal credits = amount.setScale(SCALE, RoundingMode.HALF_UP);
        if (credits.signum() == 0) return toResponse(userId, wallet);
        BigDecimal plan;
        BigDecimal purchased;
        if (credits.signum() > 0) {
            plan = BigDecimal.ZERO;
            purchased = credits;
        } else {
            Split split = split(wallet, credits.negate());
            plan = split.plan().negate();
            purchased = split.purchased().negate();
        }
        // Counted rather than random: two taps on the same button must not both apply.
        String key = "admin_adjust:" + userId + ":" + repository.countTransactions(userId);
        record(wallet, AiCreditTransaction.builder()
                .transactionType(AiCreditTransactionType.ADJUST.name())
                .planAmount(plan)
                .purchasedAmount(purchased)
                .referenceKey(key)
                .note(truncate(reason, 500))
                .createdBy(operatorId)
                .build());
        repository.updateWallet(wallet);
        return toResponse(userId, wallet);
    }

    /** The user's plan changed (PRO granted or revoked): start a period of the new plan now. */
    @Transactional
    public void onPlanChanged(UUID userId) {
        AiCreditWallet wallet = repository.lockWallet(userId);
        refill(userId, wallet, true);
    }

    // --- internals ----------------------------------------------------------------------

    private AiCreditWallet openWallet(UUID userId) {
        AiCreditWallet wallet = repository.lockWallet(userId);
        refill(userId, wallet, false);
        return wallet;
    }

    /**
     * Starts the current period when the last one ended or the plan changed: the plan bucket goes
     * to the allowance, less any debt it carried.
     */
    private void refill(UUID userId, AiCreditWallet wallet, boolean force) {
        LocalDateTime now = LocalDateTime.now(clock);
        PlanTerms plan = planTerms(userId, wallet);
        boolean due = force
                || wallet.getPlanPeriodEnd() == null
                || !now.isBefore(wallet.getPlanPeriodEnd())
                || !Objects.equals(plan.code(), wallet.getPlanCode());
        if (!due) return;
        LocalDateTime start = periodStart(plan.anchor(), plan.months(), now);
        String key = "plan_refill:" + userId + ":" + plan.code() + ":" + start;
        if (!repository.hasReference(key)) {
            BigDecimal target = plan.credits().add(wallet.getPlanBalance().min(BigDecimal.ZERO));
            record(wallet, AiCreditTransaction.builder()
                    .transactionType(AiCreditTransactionType.PLAN_REFILL.name())
                    .planAmount(target.subtract(wallet.getPlanBalance()))
                    .purchasedAmount(BigDecimal.ZERO)
                    .referenceKey(key)
                    .note(plan.code())
                    .build());
        }
        wallet.setPlanCode(plan.code());
        wallet.setPlanPeriodStart(start);
        wallet.setPlanPeriodEnd(start.plusMonths(plan.months()));
        repository.updateWallet(wallet);
    }

    /** Applies the entry to the wallet in memory and writes it; the caller saves the wallet. */
    private void record(AiCreditWallet wallet, AiCreditTransaction tx) {
        wallet.setPlanBalance(wallet.getPlanBalance().add(tx.getPlanAmount()));
        wallet.setPurchasedBalance(wallet.getPurchasedBalance().add(tx.getPurchasedAmount()));
        tx.setId(UUID.randomUUID());
        tx.setUserId(wallet.getUserId());
        tx.setAmount(tx.getPlanAmount().add(tx.getPurchasedAmount()));
        tx.setPlanBalanceAfter(wallet.getPlanBalance());
        tx.setPurchasedBalanceAfter(wallet.getPurchasedBalance());
        repository.insertTransaction(tx);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("balance", wallet.balance());
        payload.put("planBalance", wallet.getPlanBalance());
        payload.put("purchasedBalance", wallet.getPurchasedBalance());
        payload.put("delta", tx.getAmount());
        payload.put("type", tx.getTransactionType());
        payload.put("feature", tx.getFeature());
        payload.put("traceId", tx.getTraceId());
        realtime.publishAfterCommit(UserRealtimeEventType.CREDIT_CHANGED, wallet.getUserId(), payload);
    }

    /** How much of a debit each bucket takes: plan credits above zero first, then bought ones, then debt. */
    private static Split split(AiCreditWallet wallet, BigDecimal debit) {
        BigDecimal fromPlan = debit.min(wallet.getPlanBalance().max(BigDecimal.ZERO));
        BigDecimal rest = debit.subtract(fromPlan);
        BigDecimal fromPurchased = rest.min(wallet.getPurchasedBalance());
        return new Split(fromPlan.add(rest.subtract(fromPurchased)), fromPurchased);
    }

    /** cost x markup / price of a credit; a call with tokens but no price pays the fallback. */
    private BigDecimal credits(AiApiCall call) {
        if (call.getCostUsd() == null) {
            boolean usedTokens = nz(call.getInputTokens()) + nz(call.getOutputTokens()) > 0;
            if (!usedTokens) return BigDecimal.ZERO;
            log.warn("No AI_PRICING for model {}; charging the fallback for {}/{}",
                    call.getModel(), call.getFeature(), call.getOperation());
            return BigDecimal.valueOf(config.getInt(BusinessConfigKey.AI_CREDIT_FALLBACK_CREDITS_PER_CALL));
        }
        BigDecimal markup = BigDecimal.valueOf(config.getDecimal(BusinessConfigKey.AI_CREDIT_MARKUP));
        BigDecimal usdPerCredit = BigDecimal.valueOf(config.getDecimal(BusinessConfigKey.AI_CREDIT_USD_PER_CREDIT));
        return call.getCostUsd().multiply(markup).divide(usdPerCredit, SCALE, RoundingMode.HALF_UP);
    }

    private AiCreditStatus status(UUID userId, AiCreditWallet wallet) {
        BigDecimal floor = BigDecimal.valueOf(config.getInt(BusinessConfigKey.AI_CREDIT_NEGATIVE_FLOOR)).negate();
        boolean exhausted = wallet.balance().compareTo(floor) < 0 && !betaAccess.isBetaUser(userId);
        return new AiCreditStatus(wallet.balance(), exhausted);
    }

    private int required(AiCreditFeature... features) {
        return Arrays.stream(features).mapToInt(f -> config.getInt(f.minBalanceKey())).sum();
    }

    /** The plan in force and its allowance; a lapsed or unknown PRO plan falls back sensibly. */
    private PlanTerms planTerms(UUID userId, AiCreditWallet wallet) {
        AiCreditPlan subscription = repository.findPlan(userId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        boolean pro = subscription != null && subscription.getTier() != null
                && PRO_TIER.equals(subscription.getTier().trim().toUpperCase(Locale.ROOT))
                && (subscription.getExpiresAt() == null || subscription.getExpiresAt().isAfter(now));
        LocalDateTime walletStart = wallet.getCreatedAt() != null ? wallet.getCreatedAt() : now;
        if (!pro) {
            return new PlanTerms(FREE_PLAN,
                    BigDecimal.valueOf(config.getInt(BusinessConfigKey.AI_CREDIT_FREE_MONTHLY_CREDITS)), 1, walletStart);
        }
        // A PRO row written before plans existed names no plan: it gets the monthly plan's terms.
        AiCreditPlan allowance = subscription.getCreditsPerRefill() != null
                ? subscription
                : repository.findPlanByCode(DEFAULT_PRO_PLAN).orElse(subscription);
        String code = subscription.getPlanCode() != null ? subscription.getPlanCode() : DEFAULT_PRO_PLAN;
        BigDecimal credits = allowance.getCreditsPerRefill() != null ? allowance.getCreditsPerRefill() : BigDecimal.ZERO;
        int months = allowance.getRefillMonths() != null ? Math.max(1, allowance.getRefillMonths()) : 1;
        LocalDateTime anchor = subscription.getStartedAt() != null ? subscription.getStartedAt() : walletStart;
        return new PlanTerms(code, credits, months, anchor);
    }

    /** The start of the period containing {@code now}, counting whole cycles from the anchor. */
    static LocalDateTime periodStart(LocalDateTime anchor, int months, LocalDateTime now) {
        if (anchor.isAfter(now)) return now;
        long elapsed = ChronoUnit.MONTHS.between(anchor, now);
        LocalDateTime start = anchor.plusMonths(elapsed - elapsed % months);
        while (start.isAfter(now)) start = start.minusMonths(months);
        return start;
    }

    private AiCreditWalletResponse toResponse(UUID userId, AiCreditWallet wallet) {
        PlanTerms plan = planTerms(userId, wallet);
        Map<String, Integer> minBalance = new LinkedHashMap<>();
        for (AiCreditFeature feature : AiCreditFeature.values()) {
            minBalance.put(feature.name(), config.getInt(feature.minBalanceKey()));
        }
        return AiCreditWalletResponse.builder()
                .balance(wallet.balance())
                .planBalance(wallet.getPlanBalance())
                .purchasedBalance(wallet.getPurchasedBalance())
                .planCode(wallet.getPlanCode())
                .planCredits(plan.credits())
                .refillMonths(plan.months())
                .nextRefillAt(wallet.getPlanPeriodEnd())
                .negativeFloor(config.getInt(BusinessConfigKey.AI_CREDIT_NEGATIVE_FLOOR))
                .minBalance(minBalance)
                .unlimited(betaAccess.isBetaUser(userId))
                .build();
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }

    private record Split(BigDecimal plan, BigDecimal purchased) {
    }

    private record PlanTerms(String code, BigDecimal credits, int months, LocalDateTime anchor) {
    }
}
