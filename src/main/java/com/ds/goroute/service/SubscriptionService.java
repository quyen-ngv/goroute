package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.SubscriptionSummaryResponse;
import com.ds.goroute.entity.SubscriptionPlan;
import com.ds.goroute.entity.UserSubscription;
import com.ds.goroute.entity.UserSubscriptionGrant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.SubscriptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Paid plans: what they are, who is on one, and until when.
 *
 * <p>Two rules hold, and both are enforced rather than assumed.
 *
 * <ul>
 *   <li>A period is granted once per reference key. A retried payment callback or a
 *       double-tapped admin button builds the same key and the second attempt returns the first
 *       one's result instead of adding another year.</li>
 *   <li>Renewing early does not throw away what is left. A grant extends from whichever is later,
 *       now or the current end date, so somebody who renews with a week remaining keeps that
 *       week.</li>
 * </ul>
 *
 * <p>The tier itself is read through {@code AiTripRepository.getSubscriptionTier}, which every
 * feature already uses and which reports a lapsed plan as FREE. Nothing here caches or duplicates
 * that answer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionMapper mapper;
    private final UserSubscriptionBootstrapService bootstrap;
    private final PlatformTransactionManager transactionManager;

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> plans(boolean activeOnly) {
        return mapper.findPlans(activeOnly);
    }

    @Transactional
    public SubscriptionSummaryResponse summary(UUID userId) {
        bootstrap.ensureExists(userId);
        return readSummary(userId);
    }

    /**
     * The same answer without bootstrapping the row.
     *
     * <p>Separate because {@code ensureExists} runs REQUIRES_NEW, on a second connection. A caller
     * that has already locked this user's subscription row would have that nested insert wait on
     * its own lock for ever -- a self-deadlock PostgreSQL cannot detect. Writers bootstrap first,
     * while the row is unlocked, and read back through here.
     */
    private SubscriptionSummaryResponse readSummary(UUID userId) {
        UserSubscription row = mapper.findSubscription(userId);
        if (row == null) {
            return SubscriptionSummaryResponse.builder().tier("FREE").pro(false).build();
        }

        String storedTier = Objects.toString(row.getTier(), "FREE");
        LocalDateTime expiresAt = row.getExpiresAt();
        // A period that has run out is FREE here for the same reason it is FREE in the tier query:
        // one answer to "what am I on", or the badge and the feature disagree.
        boolean lapsed = expiresAt != null && !expiresAt.isAfter(LocalDateTime.now());
        String effectiveTier = lapsed ? "FREE" : storedTier;
        boolean pro = !"FREE".equalsIgnoreCase(effectiveTier);

        String planCode = pro ? row.getPlanCode() : null;
        SubscriptionPlan plan = planCode == null ? null : mapper.findPlan(planCode);

        return SubscriptionSummaryResponse.builder()
                .tier(effectiveTier)
                .planCode(planCode)
                .planName(plan == null ? null : plan.getDisplayName())
                .startedAt(row.getStartedAt())
                .expiresAt(pro ? expiresAt : null)
                .pro(pro)
                .daysRemaining(pro ? daysRemaining(expiresAt) : null)
                .build();
    }

    /**
     * Puts the account on a plan for that plan's length.
     *
     * @param referenceKey idempotency key; a blank one is replaced with a per-user sequence, which
     *                     covers an admin double-tap but not a payment retry -- those must supply
     *                     their own
     * @return the account's plan after the grant
     */
    @Transactional
    public SubscriptionSummaryResponse grant(UUID userId, String planCode, UUID operatorId,
                                             String referenceKey, String note) {
        SubscriptionPlan plan = mapper.findPlan(planCode);
        if (plan == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Subscription plan not found");
        }
        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "That subscription plan is no longer on sale");
        }
        bootstrap.ensureExists(userId);

        // Counting rather than a fresh UUID: a random key is unique by construction and therefore
        // idempotent against nothing, so two taps on the same button would each buy a period.
        String key = referenceKey == null || referenceKey.isBlank()
                ? "admin_grant:" + userId + ":" + planCode + ":" + mapper.countGrants(userId)
                : referenceKey.trim();

        UserSubscriptionGrant already = mapper.findGrantByReference(key);
        if (already != null) {
            log.info("Subscription grant {} was already applied for user {}", key, userId);
            return readSummary(userId);
        }

        // Read under the row lock, then extend from whichever is later. Extending from "now" would
        // quietly delete whatever was left of the current period every time somebody renewed early.
        LocalDateTime currentEnd = mapper.findExpiresAtForUpdate(userId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startsAt = currentEnd != null && currentEnd.isAfter(now) ? currentEnd : now;
        LocalDateTime expiresAt = startsAt.plusDays(plan.getDurationDays());

        UserSubscriptionGrant record = UserSubscriptionGrant.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .planCode(plan.getCode())
                .tier(plan.getTier())
                .durationDays(plan.getDurationDays())
                .startsAt(startsAt)
                .expiresAt(expiresAt)
                .referenceKey(key)
                .grantedBy(operatorId)
                .note(note)
                .build();
        // The insert gets its own savepoint. On PostgreSQL the unique-index violation aborts the
        // whole transaction, so catching it and then reading the summary back would fail with
        // "current transaction is aborted" and turn an already-applied grant into a 500. Rolling
        // back to the savepoint leaves this transaction able to answer.
        TransactionTemplate savepoint = new TransactionTemplate(transactionManager);
        savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        try {
            savepoint.executeWithoutResult(status -> mapper.insertGrant(record));
        } catch (DuplicateKeyException exception) {
            // Two requests with the same key raced past the read above. The other one wrote the
            // period; this one adds nothing.
            log.info("Subscription grant {} was applied by a concurrent request for user {}", key, userId);
            return readSummary(userId);
        }

        mapper.applyPlan(userId, plan.getTier(), plan.getCode(),
                currentEnd != null && currentEnd.isAfter(now) ? (LocalDateTime) null : now, expiresAt);
        log.info("Granted {} to user {} until {} (reference {})", plan.getCode(), userId, expiresAt, key);
        return readSummary(userId);
    }

    /**
     * Ends paid access now.
     *
     * <p>The ledger keeps every period that was granted; this only moves the end date, so a refund
     * or a mistaken grant can still be explained afterwards.
     */
    @Transactional
    public SubscriptionSummaryResponse revoke(UUID userId) {
        bootstrap.ensureExists(userId);
        mapper.revokePlan(userId, LocalDateTime.now());
        return readSummary(userId);
    }

    @Transactional(readOnly = true)
    public List<UserSubscriptionGrant> history(UUID userId, int limit) {
        return mapper.findGrants(userId, Math.max(1, Math.min(limit, 100)));
    }

    private Integer daysRemaining(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            return null;
        }
        long days = Duration.between(LocalDateTime.now(), expiresAt).toDays();
        return (int) Math.max(0, days);
    }
}
