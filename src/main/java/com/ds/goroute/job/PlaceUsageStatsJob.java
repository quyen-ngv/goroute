package com.ds.goroute.job;

import com.ds.goroute.service.PlaceUsageStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code places.trip_use_count} and {@code places.checkin_count} (V225): recounts the
 * places writes marked, every 30 seconds by default, and every place once a night, which
 * repairs marks lost to a restart and writers that bypass the events. The backend runs as one
 * instance by design, so there is no cross-instance lock.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlaceUsageStatsJob {

    private final PlaceUsageStatsService placeUsageStatsService;

    @Scheduled(fixedDelayString = "${goroute.jobs.place-usage-recount-delay-ms:30000}",
            initialDelayString = "${goroute.jobs.place-usage-recount-delay-ms:30000}")
    public void recountMarked() {
        try {
            int changed = placeUsageStatsService.recountMarked();
            if (changed > 0) {
                log.debug("Recounted usage of {} places", changed);
            }
        } catch (Exception exception) {
            log.error("Place usage recount failed: {}", exception.getMessage(), exception);
        }
    }

    @Scheduled(cron = "${goroute.jobs.place-usage-reconcile-cron:0 50 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void recountAll() {
        try {
            int changed = placeUsageStatsService.recountAll();
            if (changed > 0) {
                log.info("Reconciled usage counts of {} places", changed);
            }
        } catch (Exception exception) {
            log.error("Place usage reconcile failed: {}", exception.getMessage(), exception);
        }
    }
}
