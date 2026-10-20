package com.ds.goroute.event;

import com.ds.goroute.service.notification.FollowerFanoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Hands a new check-in or a newly public trip to the follower fan-out once it has committed.
 *
 * <p>On {@code followerFanoutExecutor}, never on the request thread: the author's request is long
 * finished by the time the first follower is told, and a fan-out that fails costs the followers a
 * notification, never the author their post.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowerFanoutListener {

    private final FollowerFanoutService fanoutService;

    @Async("followerFanoutExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCheckinCreated(CheckinCreatedEvent event) {
        try {
            fanoutService.fanOutCheckin(event.checkinId(), event.userId());
        } catch (RuntimeException failure) {
            log.error("FOLLOWER_FANOUT_FAILED check-in {}: {}", event.checkinId(), failure.getMessage(), failure);
        }
    }

    @Async("followerFanoutExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTripSharedPublic(TripSharedPublicEvent event) {
        try {
            fanoutService.fanOutTrip(event.tripId(), event.ownerId());
        } catch (RuntimeException failure) {
            log.error("FOLLOWER_FANOUT_FAILED trip {}: {}", event.tripId(), failure.getMessage(), failure);
        }
    }
}
