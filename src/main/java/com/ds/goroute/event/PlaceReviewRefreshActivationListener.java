package com.ds.goroute.event;

import com.ds.goroute.service.AdminPlaceReviewRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlaceReviewRefreshActivationListener {
    private final AdminPlaceReviewRefreshService reviewRefreshService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enqueueWhenActivated(PlaceActivatedEvent event) {
        try {
            reviewRefreshService.trigger(event.placeId(), 200);
        } catch (Exception exception) {
            log.error("Could not enqueue review refresh for activated place {}: {}",
                    event.placeId(), exception.getMessage());
        }
    }
}
