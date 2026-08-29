package com.ds.goroute.event;

import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.service.PassportService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.service.checkin.CheckinRewardCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckinConsequencesListener {

    private final UserCheckinRepository checkinRepository;
    private final PassportService passportService;
    private final StarService pointWallet;
    private final CheckinRewardCalculator rewardCalculator;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCheckinCreated(CheckinCreatedEvent event) {
        Optional<UserCheckin> stored = checkinRepository.findById(event.checkinId());
        if (stored.isEmpty()) {
            return;
        }
        UserCheckin checkin = stored.get();

        // Each consequence is attempted on its own. A failing reward must not cost the
        // author their passport entry, and neither must cost them the check-in.
        try {
            passportService.recordCheckin(checkin);
        } catch (RuntimeException exception) {
            log.error("Could not record the passport event for check-in {}: {}",
                    checkin.getId(), exception.getMessage());
        }

        try {
            grantReward(checkin);
        } catch (RuntimeException exception) {
            log.error("Could not grant the check-in reward for {}: {}",
                    checkin.getId(), exception.getMessage());
        }
    }

    /**
     * A check-in is rewarded once. The amount is stored on the row itself, so a replay
     * finds it already set and stops, and so the author can be told why they got what they
     * got rather than being left to guess.
     */
    private void grantReward(UserCheckin checkin) {
        if (checkin.getRewardPoints() != null) {
            return;
        }
        int earnedToday = checkinRepository.sumRewardPointsSince(
                checkin.getUserId(), LocalDate.now().atStartOfDay());
        CheckinRewardCalculator.Reward reward = rewardCalculator.calculate(checkin, earnedToday);

        // Written even when the amount is zero: "you earned nothing, and here is why" is a
        // far better answer than silence.
        if (checkinRepository.recordReward(checkin.getId(), reward.points(), reward.reason()) == 0) {
            return;
        }
        if (reward.isGranted()) {
            pointWallet.grant(checkin.getUserId(), reward.points(), "CHECKIN_EXPLORER_POINTS",
                    "checkin:" + checkin.getId(), reward.reason());
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
