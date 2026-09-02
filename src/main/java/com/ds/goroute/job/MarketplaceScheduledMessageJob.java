package com.ds.goroute.job;

import com.ds.goroute.entity.MarketplaceScheduledMessage;
import com.ds.goroute.entity.ScheduledMessageTarget;
import com.ds.goroute.service.MarketplaceScheduledMessageService;
import com.ds.goroute.type.MarketplaceScheduledMessageRunStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Turns partner automation rules into real chat messages.
 *
 * <p>Safe to run concurrently with itself. Each booking is delivered in its own transaction (the
 * service is called through the Spring proxy) and that transaction reserves the run row before it
 * writes anything: two overlapping ticks racing on the same (rule, booking) pair make the loser hit
 * the unique index, roll back, and deliver nothing. Duplicate suppression therefore lives in the
 * database, not in a lock or a "job is running" flag that a restart would lose.
 *
 * <p>Isolation follows {@link MarketplaceHoldExpiryJob}: one bad rule or one bad booking fails
 * itself, is logged with its id, and never stops the rows behind it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketplaceScheduledMessageJob {
    /** Rules considered per tick. Well past the per-organization cap times any realistic partner count. */
    static final int RULE_BATCH = 500;
    /** Bookings delivered per rule per tick; the rest are picked up 5 minutes later. */
    static final int TARGET_BATCH = 200;

    private final MarketplaceScheduledMessageService service;

    @Scheduled(fixedDelayString = "${goroute.marketplace.scheduled-message-delay-ms:300000}")
    public void sendDueScheduledMessages() {
        LocalDateTime now = LocalDateTime.now();
        int sent = 0;
        int skipped = 0;
        for (MarketplaceScheduledMessage rule : service.findEnabledRules(RULE_BATCH)) {
            try {
                for (ScheduledMessageTarget target : service.findDueTargets(rule, now, TARGET_BATCH)) {
                    MarketplaceScheduledMessageRunStatus outcome = deliver(rule, target);
                    if (outcome == MarketplaceScheduledMessageRunStatus.SENT) sent++;
                    else if (outcome == MarketplaceScheduledMessageRunStatus.SKIPPED) skipped++;
                }
            } catch (RuntimeException ex) {
                // A rule whose selection query fails must not hide the rules behind it.
                log.error("Could not evaluate scheduled message {}: {}", rule.getId(), ex.getMessage());
            }
        }
        if (sent + skipped > 0) {
            log.info("Scheduled guest messages: {} sent, {} skipped", sent, skipped);
        }
    }

    MarketplaceScheduledMessageRunStatus deliver(MarketplaceScheduledMessage rule, ScheduledMessageTarget target) {
        try {
            return service.deliver(rule, target);
        } catch (DataIntegrityViolationException duplicate) {
            // Another tick already claimed this pair. Expected under overlap, not an error.
            log.debug("Scheduled message {} was already delivered for booking {}", rule.getId(), bookingId(target));
            return null;
        } catch (RuntimeException ex) {
            log.error("Could not deliver scheduled message {} for booking {}: {}",
                    rule.getId(), bookingId(target), ex.getMessage());
            recordFailure(rule, target, ex);
            return MarketplaceScheduledMessageRunStatus.FAILED;
        }
    }

    private void recordFailure(MarketplaceScheduledMessage rule, ScheduledMessageTarget target, RuntimeException cause) {
        try {
            // The delivery transaction rolled back, so this writes the terminal marker in a new one.
            // Without it the same broken booking would be retried every 5 minutes for two days.
            service.recordFailure(rule, target, cause.getMessage());
        } catch (RuntimeException ignored) {
            log.warn("Could not record the failed run of scheduled message {} for booking {}",
                    rule.getId(), bookingId(target));
        }
    }

    private static Object bookingId(ScheduledMessageTarget target) {
        return target.isHotel() ? target.getHotelBookingId() : target.getActivityOrderId();
    }
}
