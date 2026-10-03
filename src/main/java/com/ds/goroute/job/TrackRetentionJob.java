package com.ds.goroute.job;

import com.ds.goroute.service.TrackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes raw GPS points of trips that ended more than TRACKER/RAW_RETENTION_DAYS ago
 * (TR-24). Day lines and statistics stay with the trip.
 *
 * <p>Works in bounded batches until nothing is left or {@link #MAX_BATCHES_PER_RUN} is
 * reached. A second instance running at the same time only deletes rows the first has not
 * yet reached, so the job is safe without a lock.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrackRetentionJob {

    static final int BATCH_SIZE = 500;
    static final int MAX_BATCHES_PER_RUN = 200;

    private final TrackService trackService;

    @Scheduled(cron = "${goroute.jobs.track-retention-cron:0 30 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void purgeExpiredPoints() {
        int total = 0;
        try {
            for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
                int deleted = trackService.purgeExpiredChunks(BATCH_SIZE);
                total += deleted;
                if (deleted < BATCH_SIZE) {
                    break;
                }
            }
        } catch (Exception exception) {
            log.error("Track retention purge failed after {} chunks: {}", total, exception.getMessage(), exception);
            return;
        }
        if (total > 0) {
            log.info("Purged {} expired track chunks", total);
        }
    }
}
