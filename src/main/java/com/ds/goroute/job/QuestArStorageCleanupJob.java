package com.ds.goroute.job;

import com.ds.goroute.service.ImageStorageCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Frees the space AR library files (§3.15) take once nothing uses them: a GLB, USDZ or thumbnail
 * uploaded on the console or by a creator in the app but never saved on an object. Replaced and
 * deleted objects' files are removed when that happens; this catches the rest. Both writers of
 * {@code quest-ar/} save the object right after uploading its files, so two days is a safe margin.
 * Single-instance (no Redis lock), daily by default.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestArStorageCleanupJob {

    static final String PREFIX = "quest-ar/";
    static final Duration MIN_AGE = Duration.ofDays(2);
    private static final int BATCH_SIZE = 500;

    private final ImageStorageCleanupService cleanupService;

    @Scheduled(cron = "${goroute.jobs.quest-ar-storage-cleanup-cron:0 15 3 * * *}")
    public void run() {
        try {
            cleanupService.deleteOrphansUnder(PREFIX, MIN_AGE, BATCH_SIZE);
        } catch (Exception exception) {
            // A failed reference query aborts before anything is deleted; the next run retries.
            log.error("AR storage cleanup failed: {}", exception.getMessage(), exception);
        }
    }
}
