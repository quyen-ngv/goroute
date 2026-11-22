package com.ds.goroute.localevent.service;

import com.ds.goroute.localevent.domain.LocalEventImage;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ImageUploadOutcome;
import com.ds.goroute.service.ImageUploadRequest;
import com.ds.goroute.type.LocalEventImageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Re-hosts crawled event images (SPEC_V2 §1.6). Each PENDING image is downloaded through the
 * SSRF-guarded path, recognised by its bytes (JPEG/PNG/WEBP), run through image moderation like
 * any upload, and stored under {@code events/<eventId>/}. Images are never hotlinked: until this
 * succeeds the event shows no image. A failure is retried on the next runs; after
 * {@value #MAX_ATTEMPTS} attempts (or a moderation rejection) the image is FAILED with a reason
 * and the console can send it back to PENDING.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalEventImageService {

    static final int MAX_ATTEMPTS = 3;
    static final int BATCH_SIZE = 20;
    private static final int REASON_MAX = 300;

    private final LocalEventRepository repository;
    private final FileUploadService fileUploadService;

    Clock clock = Clock.systemUTC();

    /** @return how many images were stored in this run */
    public int processPending() {
        List<LocalEventImage> pending = repository.findPendingImages(MAX_ATTEMPTS, BATCH_SIZE);
        int stored = 0;
        for (LocalEventImage image : pending) {
            try {
                if (rehost(image)) {
                    stored++;
                }
            } catch (RuntimeException exception) {
                // Keep going: one bad row must not block the ones behind it.
                log.warn("Event image {} could not be processed: {}", image.getId(), exception.getClass().getSimpleName());
                recordFailure(image, "Unexpected error while storing the image", false);
            }
        }
        return stored;
    }

    boolean rehost(LocalEventImage image) {
        ImageUploadOutcome outcome = fileUploadService.uploadImageFromPublicUrl(ImageUploadRequest.of(
                null, ImageUploadRequest.ImageEntryPoint.LOCAL_EVENT, "events/" + image.getEventId()),
                image.getSourceUrl());
        if (!outcome.isAccepted()) {
            recordFailure(image, outcome.failureMessage(), outcome.isContentRejection());
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!repository.markImageDone(image.getId(), outcome.url(), now)) {
            // Replaced or retried meanwhile; the stored object becomes an orphan for the sweep.
            return false;
        }
        if (image.getPosition() != null && image.getPosition() == 0) {
            repository.updateCoverFromImage(image.getEventId(), image.getId());
        }
        return true;
    }

    private void recordFailure(LocalEventImage image, String reason, boolean permanent) {
        int attempts = (image.getAttempts() == null ? 0 : image.getAttempts()) + 1;
        String status = permanent || attempts >= MAX_ATTEMPTS
                ? LocalEventImageStatus.FAILED.name()
                : LocalEventImageStatus.PENDING.name();
        String clipped = reason == null ? "Unknown error"
                : reason.length() > REASON_MAX ? reason.substring(0, REASON_MAX) : reason;
        repository.markImageAttemptFailed(image.getId(), status, clipped, LocalDateTime.now(clock));
    }
}
