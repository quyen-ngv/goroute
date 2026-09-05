package com.ds.goroute.job;

import com.ds.goroute.service.RemovedCheckinPhotoCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reclaims database rows and managed storage objects from soft-deleted check-ins. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RemovedCheckinPhotoCleanupJob {

    private static final int BATCH_SIZE = 100;

    private final RemovedCheckinPhotoCleanupService cleanupService;

    @Scheduled(fixedDelayString = "${goroute.jobs.removed-checkin-photo-cleanup-delay-ms:3600000}")
    public void purgeRemovedCheckinPhotos() {
        try {
            int purged = cleanupService.purgeRemovedCheckinPhotos(BATCH_SIZE);
            if (purged > 0) {
                log.info("Purged photo rows for {} soft-deleted check-ins", purged);
            }
        } catch (Exception exception) {
            log.error("Removed check-in photo cleanup failed: {}", exception.getMessage(), exception);
        }
    }
}
