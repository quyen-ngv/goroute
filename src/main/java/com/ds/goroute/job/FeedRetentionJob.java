package com.ds.goroute.job;

import com.ds.goroute.service.FeedFeedbackService;
import com.ds.goroute.service.FeedImpressionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly clean-up of the Feed's behaviour tables (V245): impressions older than
 * FEED.IMPRESSION_RETENTION_DAYS, and hides that were undone or expired 90 days ago. Deletes in
 * slices so one night with a large backlog never holds a long lock. The backend runs as one
 * instance by design, so there is no cross-instance lock.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeedRetentionJob {

    private final FeedImpressionService impressionService;
    private final FeedFeedbackService feedbackService;

    @Scheduled(cron = "${goroute.jobs.feed-retention-cron:0 10 4 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void purge() {
        try {
            int impressions = impressionService.purgeExpired();
            int feedback = feedbackService.purgeFinished();
            if (impressions > 0 || feedback > 0) {
                log.info("Feed retention removed {} impressions and {} finished hides", impressions, feedback);
            }
        } catch (Exception exception) {
            log.error("Feed retention failed: {}", exception.getMessage(), exception);
        }
    }
}
