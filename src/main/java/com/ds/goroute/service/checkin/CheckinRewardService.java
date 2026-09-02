package com.ds.goroute.service.checkin;

import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.repository.UserCheckinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Decides a check-in's reward once, wherever the question is asked first.
 *
 * <p>Two callers need the answer: the request that creates the check-in, so the author can be told
 * what they earned while they are still looking at the screen, and the after-commit listener that
 * moves the points into the wallet. Both go through here, so the number cannot come out differently
 * depending on who asked.
 *
 * <p>Writing the amount on the check-in row is what makes that safe. A second call finds it already
 * set and returns it rather than recalculating against a daily total that has since moved.
 */
@Service
@RequiredArgsConstructor
public class CheckinRewardService {

    private final UserCheckinRepository checkinRepository;
    private final CheckinRewardCalculator calculator;

    /**
     * Works out this check-in's reward and stores it, unless it already has one.
     *
     * <p>Also sets the fields on the passed entity, so a caller holding it can put the amount
     * straight into its response without reading the row back.
     *
     * @return what is now recorded against this check-in
     */
    @Transactional
    public CheckinRewardCalculator.Reward record(UserCheckin checkin) {
        if (checkin.getRewardPoints() != null) {
            return new CheckinRewardCalculator.Reward(
                    checkin.getRewardPoints(),
                    CheckinRewardCalculator.parseReasonCodes(checkin.getRewardReason()));
        }

        int earnedToday = checkinRepository.sumRewardPointsSince(
                checkin.getUserId(), LocalDate.now().atStartOfDay());
        CheckinRewardCalculator.Reward reward = calculator.calculate(checkin, earnedToday);

        // Written even when the amount is zero: "you earned nothing, and here is why" is a far
        // better answer than silence.
        if (checkinRepository.recordReward(checkin.getId(), reward.points(), reward.reason()) == 0) {
            // Somebody else got there first, or the row is gone. Either way this call did not
            // decide the amount and must not claim one.
            return new CheckinRewardCalculator.Reward(0, List.of());
        }
        checkin.setRewardPoints(reward.points());
        checkin.setRewardReason(reward.reason());
        return reward;
    }
}
