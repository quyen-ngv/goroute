package com.ds.goroute.job;

import com.ds.goroute.mapper.MapCacheMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Nightly upkeep of the map caches: expired answers go, and road legs nobody has asked for in
 * {@link #UNUSED_LEG_DAYS} days go, so roads that changed get routed afresh. Places are kept.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MapCacheCleanupJob {

    static final int UNUSED_LEG_DAYS = 180;

    private final MapCacheMapper cache;

    @Scheduled(cron = "${goroute.jobs.map-cache-cleanup-cron:0 55 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void cleanUp() {
        try {
            LocalDateTime now = LocalDateTime.now();
            int answers = cache.deleteExpiredResponses(now);
            int legs = cache.deleteLegsUnusedSince(now.minusDays(UNUSED_LEG_DAYS));
            if (answers > 0 || legs > 0) {
                log.info("Map cache cleanup: {} expired answers, {} unused road legs", answers, legs);
            }
        } catch (Exception exception) {
            log.error("Map cache cleanup failed: {}", exception.getMessage(), exception);
        }
    }
}
