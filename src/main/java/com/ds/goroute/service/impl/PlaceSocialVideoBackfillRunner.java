package com.ds.goroute.service.impl;

import com.ds.goroute.entity.SocialLocationJob;
import com.ds.goroute.mapper.SocialLocationJobMapper;
import com.ds.goroute.service.PlaceSocialVideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlaceSocialVideoBackfillRunner {
    private static final int BATCH_SIZE = 100;

    private final SocialLocationJobMapper socialLocationJobMapper;
    private final PlaceSocialVideoService placeSocialVideoService;

    @Async("placeImportJobExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void backfillExistingSocialJobs() {
        int offset = 0;
        int processed = 0;
        while (true) {
            List<SocialLocationJob> jobs = socialLocationJobMapper
                    .findCompletedForVideoLinkBackfill(BATCH_SIZE, offset);
            if (jobs.isEmpty()) {
                break;
            }
            for (SocialLocationJob job : jobs) {
                try {
                    placeSocialVideoService.syncSocialJob(job);
                } catch (Exception e) {
                    log.warn("Could not backfill place-video links for social job {}: {}",
                            job.getId(), e.getMessage());
                }
            }
            processed += jobs.size();
            offset += jobs.size();
            if (jobs.size() < BATCH_SIZE) {
                break;
            }
        }
        if (processed > 0) {
            log.info("Place social-video link backfill finished: jobs={}", processed);
        }
    }
}
