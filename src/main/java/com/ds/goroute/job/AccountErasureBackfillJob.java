package com.ds.goroute.job;

import com.ds.goroute.service.account.AccountErasureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Erases accounts that were deleted before erasure existed (a soft delete that kept the email,
 * name and content, and that a Google/Apple sign-in could restore), so every deleted account ends
 * up the way Privacy Policy 2.0 §10.2 describes. Irreversible. Once those are done the query
 * finds nothing and the job is idle; deletions made today are erased at once and never reach it.
 *
 * <p>Each account is its own transaction; one that fails is logged and tried again next night.
 * Turn off with {@code goroute.jobs.account-erasure-backfill-enabled=false}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "goroute.jobs.account-erasure-backfill-enabled", havingValue = "true",
        matchIfMissing = true)
public class AccountErasureBackfillJob {

    static final int ACCOUNTS_PER_RUN = 100;

    private final AccountErasureService erasureService;

    @Scheduled(cron = "${goroute.jobs.account-erasure-backfill-cron:0 20 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void eraseDeletedAccounts() {
        List<UUID> userIds = erasureService.findUnerased(ACCOUNTS_PER_RUN);
        int erased = 0;
        for (UUID userId : userIds) {
            try {
                erasureService.erase(userId);
                erased++;
            } catch (Exception exception) {
                log.error("Erasing deleted account {} failed: {}", userId, exception.getMessage(), exception);
            }
        }
        if (!userIds.isEmpty()) {
            log.info("Erased {} of {} previously deleted accounts", erased, userIds.size());
        }
    }
}
