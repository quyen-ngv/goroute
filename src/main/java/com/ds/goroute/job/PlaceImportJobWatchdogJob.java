package com.ds.goroute.job;

import com.ds.goroute.service.PlaceImportJobWatchdogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Closes nationwide and place-detail-refresh jobs whose worker callback never arrived.
 *
 * <p>Safe to interrupt and to run repeatedly: it only probes jobs that have gone quiet,
 * and the closing update is conditional on the job still being QUEUED or PROCESSING, so
 * a callback that lands in the same moment always wins.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlaceImportJobWatchdogJob {

    private final PlaceImportJobWatchdogService watchdogService;

    @Scheduled(fixedDelayString = "${goroute.jobs.place-import-watchdog-delay-ms:120000}")
    public void reconcileStalledJobs() {
        try {
            int finalized = watchdogService.reconcileStalledJobs();
            if (finalized > 0) {
                log.info("Closed {} stalled place import jobs", finalized);
            }
        } catch (Exception exception) {
            log.error("Place import job watchdog run failed: {}", exception.getMessage(), exception);
        }
    }
}
