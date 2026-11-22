package com.ds.goroute.job;

import com.ds.goroute.service.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes Idempotency-Key records past their TTL ({@link IdempotencyService#TTL}, 30 days). Expired rows are already ignored by
 * the claim, so this only keeps the table small. Batched deletes; safe to overlap, since a
 * second run only deletes rows the first has not reached. The backend runs as one instance
 * by design, so there is no cross-instance lock.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyRecordCleanupJob {

    private final IdempotencyService idempotencyService;

    @Scheduled(cron = "${goroute.jobs.idempotency-cleanup-cron:0 15 * * * *}", zone = "Asia/Ho_Chi_Minh")
    public void purgeExpired() {
        try {
            int deleted = idempotencyService.purgeExpired();
            if (deleted > 0) {
                log.info("Purged {} expired idempotency records", deleted);
            }
        } catch (Exception exception) {
            log.error("Idempotency record purge failed: {}", exception.getMessage(), exception);
        }
    }
}
