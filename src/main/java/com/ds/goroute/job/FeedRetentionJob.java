package com.ds.goroute.job;

import com.ds.goroute.service.FeedFeedbackService;
import com.ds.goroute.service.FeedImpressionService;
import com.ds.goroute.mapper.FeedEventMapper;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly clean-up of the Feed's behaviour tables (V245, V267): impressions older than
 * FEED.IMPRESSION_RETENTION_DAYS, hides that were undone or expired 90 days ago, and the
 * feed_events daily partitions (next week's made, those past FEED.EVENTS_RETENTION_DAYS dropped). Deletes in
 * slices so one night with a large backlog never holds a long lock. The backend runs as one
 * instance by design, so there is no cross-instance lock.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeedRetentionJob {

    /** Partitions made ahead, so a week without this job still writes into day partitions. */
    static final int EVENT_PARTITIONS_AHEAD_DAYS = 7;

    private final FeedImpressionService impressionService;
    private final FeedFeedbackService feedbackService;
    private final FeedEventMapper eventMapper;
    private final BusinessConfigService config;

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
        try {
            int dropped = eventMapper.maintainPartitions(EVENT_PARTITIONS_AHEAD_DAYS,
                    config.getInt(BusinessConfigKey.FEED_EVENTS_RETENTION_DAYS));
            if (dropped > 0) {
                log.info("Feed retention dropped {} feed_events partitions", dropped);
            }
        } catch (Exception exception) {
            log.error("Feed events partition upkeep failed: {}", exception.getMessage(), exception);
        }
    }
}
