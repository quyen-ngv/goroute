package com.ds.goroute.job;

import com.ds.goroute.service.MapSearchRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes per-user map search counters older than MAP_SEARCH/USAGE_RETENTION_DAYS. One row
 * per user, day and operation, so a single delete per night stays small.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MapSearchUsageRetentionJob {

    private final MapSearchRateLimiter rateLimiter;

    @Scheduled(cron = "${goroute.jobs.map-search-usage-retention-cron:0 45 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void purgeExpiredUsage() {
        try {
            int deleted = rateLimiter.purgeExpiredUsage();
            if (deleted > 0) {
                log.info("Purged {} expired map search usage rows", deleted);
            }
        } catch (Exception exception) {
            log.error("Map search usage purge failed: {}", exception.getMessage(), exception);
        }
    }
}
