package com.ds.goroute.job;

import com.ds.goroute.service.ImageStorageCleanupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Frees storage for deleted and edited records. Deleting a record no longer deletes its files
 * (they are often shared with other rows), so this sweep is the one place files of most
 * entities are removed: anything no database row names, untouched for 14 days.
 *
 * <p>Off unless {@code goroute.jobs.storage-orphan-sweep-enabled=true}: turn it on only after a
 * dry run of {@code POST /v1/api/admin/images/cleanup/orphaned} on that environment looks right.
 * Single-instance (no Redis lock).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "goroute.jobs.storage-orphan-sweep-enabled", havingValue = "true")
public class StorageOrphanSweepJob {

    private final ImageStorageCleanupService cleanupService;
    private final int limit;

    public StorageOrphanSweepJob(ImageStorageCleanupService cleanupService,
                                 @Value("${goroute.jobs.storage-orphan-sweep-limit:5000}") int limit) {
        this.cleanupService = cleanupService;
        this.limit = limit;
    }

    @Scheduled(cron = "${goroute.jobs.storage-orphan-sweep-cron:0 45 3 * * *}")
    public void run() {
        try {
            cleanupService.sweepOrphans(limit);
        } catch (Exception exception) {
            // A failed reference query aborts before anything is deleted; the next run retries.
            log.error("Storage orphan sweep failed: {}", exception.getMessage(), exception);
        }
    }
}
