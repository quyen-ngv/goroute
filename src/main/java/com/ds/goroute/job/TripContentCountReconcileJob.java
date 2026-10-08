package com.ds.goroute.job;

import com.ds.goroute.service.TripService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly repair of {@code trips.activity_count} and {@code trips.memory_count} (V216), the
 * ranking inputs of public trip search. Every normal write recounts them already; this
 * catches writers that bypass those paths, such as an admin deleting a photo from the media
 * library. One set-based UPDATE that touches only drifted rows. The backend runs as one
 * instance by design, so there is no cross-instance lock.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TripContentCountReconcileJob {

    private final TripService tripService;

    @Scheduled(cron = "${goroute.jobs.trip-content-count-reconcile-cron:0 40 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void reconcile() {
        try {
            int fixed = tripService.reconcileContentCounts();
            if (fixed > 0) {
                log.info("Reconciled activity/memory counts of {} trips", fixed);
            }
        } catch (Exception exception) {
            log.error("Trip content count reconcile failed: {}", exception.getMessage(), exception);
        }
    }
}
