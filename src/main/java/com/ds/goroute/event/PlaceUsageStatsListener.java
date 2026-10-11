package com.ds.goroute.event;

import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.service.PlaceUsageStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

/**
 * Marks the places whose usage counts (V225) a committed write may have changed. Only marks:
 * the recount runs on its own schedule ({@code PlaceUsageStatsJob}), so a burst of writes (a
 * generated trip of thirty stops) costs one recount per place, and a write rolled back marks
 * nothing.
 */
@Component
@RequiredArgsConstructor
public class PlaceUsageStatsListener {

    private final PlaceUsageStatsService placeUsageStatsService;
    private final UserCheckinRepository checkinRepository;

    /** {@code fallbackExecution}: a write outside a transaction marks at once. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPlaceUsageChanged(PlaceUsageChangedEvent event) {
        placeUsageStatsService.markChanged(event.placeReferences());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCheckinCreated(CheckinCreatedEvent event) {
        markCheckinPlace(event.checkinId());
    }

    /** The check-in is only hidden ({@code is_removed}), so its place can still be read. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCheckinRemoved(CheckinRemovedEvent event) {
        markCheckinPlace(event.checkinId());
    }

    private void markCheckinPlace(UUID checkinId) {
        checkinRepository.findById(checkinId)
                .map(UserCheckin::getPlaceId)
                .ifPresent(placeId -> placeUsageStatsService.markChanged(List.of(placeId.toString())));
    }
}
