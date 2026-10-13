package com.ds.goroute.job;

import com.ds.goroute.service.HiddenImageService;
import com.ds.goroute.service.ImageModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Finishes image checks the background worker never completed (a restart dropped the
 * queued task), so no stored image stays unchecked, and re-syncs the hidden-image set with
 * the database. Single-instance like every job here (no Redis lock).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageModerationRetryJob {

    /** Far longer than one provider call, so a check still running is never picked up twice. */
    private static final Duration STALE_AFTER = Duration.ofMinutes(5);
    private static final int BATCH_SIZE = 50;

    private final ImageModerationService imageModerationService;
    private final HiddenImageService hiddenImageService;

    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void resumeStaleChecks() {
        int resumed = imageModerationService.resumeStale(STALE_AFTER, BATCH_SIZE);
        if (resumed > 0) {
            log.info("Image moderation: resumed {} unfinished checks", resumed);
        }
        hiddenImageService.reload();
    }
}
