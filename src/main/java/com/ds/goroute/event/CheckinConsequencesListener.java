package com.ds.goroute.event;

import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.service.PassportService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.service.checkin.CheckinRewardCalculator;
import com.ds.goroute.service.checkin.CheckinRewardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Everything that follows a check-in but must never endanger it (CHK-09, CHK-10, PAS-02).
 *
 * <p>Runs after the check-in is committed, and off the request thread. The author cares
 * that their photos and words were saved; a stamp or a handful of points arriving a few
 * seconds later is fine, and a failure here has to leave the check-in untouched rather
 * than roll it back.
 *
 * <p>Each consequence also gets a transaction of its own. Sharing one was what made the
 * try/catch around each of them a promise the code could not keep: {@code recordCheckin} is
 * itself transactional, so a stamp rule that threw marked the shared transaction rollback-only
 * and took the points that had already been granted down with it at commit time — after the
 * catch block had logged the failure as contained.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckinConsequencesListener {

    private final UserCheckinRepository checkinRepository;
    private final PassportService passportService;
    private final StarService pointWallet;
    private final CheckinRewardService rewardService;
    private final PlatformTransactionManager transactionManager;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCheckinCreated(CheckinCreatedEvent event) {
        Optional<UserCheckin> stored = checkinRepository.findById(event.checkinId());
        if (stored.isEmpty()) {
            return;
        }
        UserCheckin checkin = stored.get();

        // Each consequence is attempted on its own, in a transaction of its own. A failing
        // reward must not cost the author their passport entry, and neither must cost them
        // the check-in.
        inOwnTransaction("passport event", checkin, () -> passportService.recordCheckin(checkin));
        inOwnTransaction("reward", checkin, () -> grantReward(checkin));
    }

    /**
     * Runs one consequence, alone, and lets nothing out.
     *
     * <p>A failure is logged with the check-in id rather than retried: every consequence in here
     * is keyed on that check-in — the passport stamp on the visit, the ledger entry on
     * {@code checkin:<id>} — so replaying the event, by hand or by a later pass, adds nothing the
     * first attempt already did and costs nothing when it did not.
     */
    private void inOwnTransaction(String what, UserCheckin checkin, Runnable consequence) {
        TransactionTemplate own = new TransactionTemplate(transactionManager);
        own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            own.executeWithoutResult(status -> consequence.run());
        } catch (RuntimeException exception) {
            log.error("CHECKIN_CONSEQUENCE_FAILED {} for check-in {} (user {}): {}",
                    what, checkin.getId(), checkin.getUserId(), exception.getMessage(), exception);
        }
    }

    /**
     * Moves an already-decided reward into the wallet.
     *
     * <p>The amount is normally worked out and stored by the request that created the check-in, so
     * that the response could show it. {@link CheckinRewardService#record} therefore usually finds
     * it set and simply hands it back; it recalculates only for a check-in that reached here
     * without one. Either way the ledger entry is keyed on the check-in, so a replay adds nothing.
     */
    private void grantReward(UserCheckin checkin) {
        CheckinRewardCalculator.Reward reward = rewardService.record(checkin);
        if (reward.isGranted()) {
            pointWallet.grant(checkin.getUserId(), reward.points(), "CHECKIN_EXPLORER_POINTS",
                    "checkin:" + checkin.getId(), "Check-in reward (" + reward.reason() + ")");
        }
    }

    /**
     * A private check-in is still part of its author's own history; it simply stops
     * appearing anywhere other people look.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCheckinRemoved(CheckinRemovedEvent event) {
        log.debug("Check-in {} was hidden by its author at {}", event.checkinId(), LocalDateTime.now());
    }
}
