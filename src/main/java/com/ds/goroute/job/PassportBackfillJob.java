package com.ds.goroute.job;

import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.PassportService;
import com.ds.goroute.type.BusinessConfigKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Brings activity check-ins recorded before the passport existed into its event stream
 * (PAS-02).
 *
 * <p>Runs in batches and is safe to interrupt: each batch only picks up rows that have no
 * event yet, so restarting after a failure resumes rather than duplicating.
 *
 * <p>It exists because of what happens without it -- somebody who has used the app for a
 * year opens their passport and finds it empty, which reads as the app having lost their
 * history rather than as a feature that started yesterday.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PassportBackfillJob {

    private static final int BATCH_SIZE = 200;

    private final PassportService passportService;
    private final BusinessConfigService config;

    @Scheduled(fixedDelayString = "${goroute.jobs.passport-backfill-delay-ms:600000}")
    public void backfill() {
        if (!config.getBoolean(BusinessConfigKey.PASSPORT_ENABLED)) {
            return;
        }
        int created = passportService.backfillLegacyActivityCheckins(BATCH_SIZE);
        if (created > 0) {
            log.info("Imported {} legacy activity check-ins into the passport", created);
        }
    }
}
