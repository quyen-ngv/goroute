package com.ds.goroute.job;

import com.ds.goroute.feed.index.FeedIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps feed_items (V249) current for the ranked feed:
 *
 * <ul>
 *   <li>every 30 s: index what the triggers queued, then recount and rescore the rows marked
 *       stats_dirty (a like, a comment, a save, a new viewer...);
 *   <li>03:30: queue recent and long-unscored rows again (repairs what no trigger sees);
 *   <li>02:30: rebuild user_author_affinity from 90 days of interactions.
 * </ul>
 *
 * The backend runs as one instance by design, so there is no cross-instance lock.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeedIndexJob {

    private final FeedIndexService indexService;

    @Scheduled(fixedDelayString = "${goroute.jobs.feed-index-delay-ms:30000}",
            initialDelayString = "${goroute.jobs.feed-index-initial-delay-ms:20000}")
    public void indexAndRescore() {
        try {
            int indexed = indexService.drainQueue();
            int rescored = indexService.refreshStats();
            if (indexed > 0 || rescored > 0) {
                log.debug("Feed index: {} posts indexed, {} rescored, {} still queued",
                        indexed, rescored, indexed > 0 ? indexService.queued() : 0);
            }
        } catch (Exception exception) {
            log.error("Feed index run failed: {}", exception.getMessage(), exception);
        }
    }

    @Scheduled(cron = "${goroute.jobs.feed-reconcile-cron:0 30 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void reconcile() {
        try {
            log.info("Feed reconcile queued {} posts", indexService.reconcile());
        } catch (Exception exception) {
            log.error("Feed reconcile failed: {}", exception.getMessage(), exception);
        }
    }

    @Scheduled(cron = "${goroute.jobs.feed-affinity-cron:0 30 2 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void rebuildAffinity() {
        try {
            log.info("Feed affinity rebuilt: {} viewer-author pairs", indexService.rebuildAffinity());
        } catch (Exception exception) {
            log.error("Feed affinity rebuild failed: {}", exception.getMessage(), exception);
        }
    }
}
