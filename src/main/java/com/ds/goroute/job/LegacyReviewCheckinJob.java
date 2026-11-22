package com.ds.goroute.job;

import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.UserCheckinService;
import com.ds.goroute.type.BusinessConfigKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Turns reviews written outside the check-in flow into check-ins (epic 08 §2.7).
 *
 * <p>A rated check-in already writes its review in the same transaction, so what is left
 * are reviews from the old review composer and from place contributions. One small batch
 * per run; a converted review is linked to its check-in, so the next run never picks it
 * again and an interrupted run simply resumes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LegacyReviewCheckinJob {

    private static final int BATCH_SIZE = 200;

    private final UserCheckinService checkinService;
    private final BusinessConfigService config;

    @Scheduled(initialDelayString = "${goroute.jobs.legacy-review-checkin-initial-delay-ms:300000}",
            fixedDelayString = "${goroute.jobs.legacy-review-checkin-delay-ms:1800000}")
    public void convert() {
        if (!config.getBoolean(BusinessConfigKey.CHECKIN_ENABLED)) {
            return;
        }
        int created = checkinService.convertLegacyReviews(BATCH_SIZE);
        if (created > 0) {
            log.info("Converted {} legacy reviews into check-ins", created);
        }
    }
}
