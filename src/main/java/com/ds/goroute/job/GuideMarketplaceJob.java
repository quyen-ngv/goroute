package com.ds.goroute.job;

import com.ds.goroute.mapper.GuideMapper;
import com.ds.goroute.service.GuideBookingService;
import com.ds.goroute.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * The three things in the guide marketplace that have to happen on a clock rather than in
 * response to somebody pressing a button.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GuideMarketplaceJob {

    private static final int BATCH_SIZE = 200;

    private final GuideBookingService bookingService;
    private final GuideMapper guideMapper;
    private final StorageService storageService;

    /**
     * Lapses requests the guide never answered, so a traveller is not left waiting on
     * somebody who has stopped using the app.
     */
    @Scheduled(fixedDelayString = "${goroute.jobs.guide-request-expiry-delay-ms:900000}")
    public void expireStaleRequests() {
        int expired = bookingService.expireStaleRequests(BATCH_SIZE);
        if (expired > 0) {
            log.info("Expired {} unanswered guide booking requests", expired);
        }
    }

    /**
     * Releases payouts for bookings that are complete, past the complaint window and not
     * frozen. Paying earlier than that has no way back if a dispute then arrives.
     */
    @Scheduled(fixedDelayString = "${goroute.jobs.guide-payout-delay-ms:3600000}")
    public void releaseDuePayouts() {
        int released = bookingService.releaseDuePayouts(BATCH_SIZE);
        if (released > 0) {
            log.info("Released {} guide payouts", released);
        }
    }

    /**
     * Disposes of identity documents whose retention period has passed.
     *
     * <p>Automatic because the alternative is that they are kept forever. Holding somebody's
     * papers is an obligation with an end date, and an end date nobody enforces is not one.
     */
    @Scheduled(cron = "${goroute.jobs.guide-document-purge-cron:0 30 3 * * *}")
    public void purgeExpiredDocuments() {
        int purged = 0;
        for (Map<String, Object> document : guideMapper.findDocumentsToPurge(BATCH_SIZE)) {
            UUID id = (UUID) document.get("id");
            String fileUrl = String.valueOf(document.get("file_url"));
            try {
                if (fileUrl != null && !fileUrl.isBlank()) {
                    storageService.deleteFile(fileUrl);
                }
                guideMapper.markDocumentPurged(id);
                purged++;
            } catch (RuntimeException exception) {
                // Left for the next run rather than marked done: a document recorded as
                // deleted but still in storage is the worst of both outcomes.
                log.error("Could not purge guide document {}: {}", id, exception.getMessage());
            }
        }
        if (purged > 0) {
            log.info("Purged {} expired guide identity documents", purged);
        }
    }
}
