package com.ds.goroute.localevent.job;

import com.ds.goroute.localevent.service.LocalEventImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Re-hosts pending local event images, up to 20 per run, every minute by default
 * ({@code goroute.jobs.local-event-images.delay}, milliseconds). Single instance only, like every
 * job in this application (no distributed lock).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalEventImageJob {

    private final LocalEventImageService imageService;

    @Scheduled(fixedDelayString = "${goroute.jobs.local-event-images.delay:60000}")
    public void rehostPendingImages() {
        int stored = imageService.processPending();
        if (stored > 0) {
            log.info("Re-hosted {} local event images", stored);
        }
    }
}
