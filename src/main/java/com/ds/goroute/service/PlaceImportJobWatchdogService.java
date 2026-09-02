package com.ds.goroute.service;

import com.ds.goroute.config.PlaceImportWatchdogProperties;
import com.ds.goroute.entity.PlaceImportJob;
import com.ds.goroute.mapper.PlaceImportJobMapper;
import com.ds.goroute.thirdparty.scrape.ScrapeJobStatusResponse;
import com.ds.goroute.thirdparty.scrape.ScrapeServiceClient;
import com.ds.goroute.type.PlaceImportJobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Brings nationwide and place-detail-refresh jobs back in step with the scrape worker.
 *
 * <p>Both job types learn their outcome only from a worker callback on
 * {@code /v1/api/internal/place-import-jobs/**}. Any callback that never lands -- a
 * missing internal token, a worker restart, a network blip -- leaves the job in
 * PROCESSING forever, and because {@code findActiveNationwideJob} and
 * {@code findActivePlaceDetailRefreshJob} treat PROCESSING as active, one lost callback
 * also blocks every later job of that type.
 *
 * <p>So the outcome is verified rather than awaited: jobs that have gone quiet are
 * probed against the worker's own job store, and a job the worker no longer knows about
 * is closed instead of being left to block the queue.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceImportJobWatchdogService {

    private static final int MAX_JOBS_PER_RUN = 20;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final PlaceImportJobMapper jobMapper;
    private final ScrapeServiceClient scrapeServiceClient;
    private final PlaceImportWatchdogProperties properties;

    public int reconcileStalledJobs() {
        LocalDateTime now = LocalDateTime.now();
        List<PlaceImportJob> stalled = jobMapper.findStalledWorkerJobs(
                now.minus(properties.getSilentAfter()), MAX_JOBS_PER_RUN);
        int finalized = 0;
        for (PlaceImportJob job : stalled) {
            Verdict verdict = probe(job, now);
            if (verdict == null) {
                continue;
            }
            int updated = jobMapper.markJobTerminal(
                    job.getId(), verdict.status(), truncate(verdict.reason()), now);
            if (updated > 0) {
                finalized++;
                log.warn("Watchdog closed {} job {} (worker job {}) as {}: {}",
                        job.getSourceType(), job.getId(), job.getPythonJobId(), verdict.status(), verdict.reason());
            }
        }
        return finalized;
    }

    /** Returns the outcome the worker reports, or null while the job is still alive. */
    private Verdict probe(PlaceImportJob job, LocalDateTime now) {
        if (job.getPythonJobId() == null || job.getPythonJobId().isBlank()) {
            return closeIfAbandoned(job, now, "Scrape worker never acknowledged this job");
        }

        ScrapeJobStatusResponse worker = scrapeServiceClient.pollJob(job.getPythonJobId());
        if (worker == null || worker.getStatus() == null || worker.getStatus().isBlank()) {
            return closeIfAbandoned(job, now,
                    "Scrape worker no longer reports job " + job.getPythonJobId()
                            + "; its result callback was lost");
        }

        return switch (worker.getStatus().toLowerCase(Locale.ROOT)) {
            case "completed" -> new Verdict(PlaceImportJobStatus.COMPLETED,
                    "Recovered from the scrape worker; the completion callback was lost");
            case "failed" -> new Verdict(PlaceImportJobStatus.FAILED, workerError(worker));
            case "cancelled" -> new Verdict(PlaceImportJobStatus.CANCELLED,
                    "Cancelled on the scrape worker; the cancellation callback was lost");
            // pending / running: the worker is still on it, leave the job alone.
            default -> null;
        };
    }

    /**
     * Closes a job the worker cannot account for, but only after {@code abandonAfter}:
     * the worker store is in-memory and a short outage or restart must not be mistaken
     * for a dead job.
     */
    private Verdict closeIfAbandoned(PlaceImportJob job, LocalDateTime now, String reason) {
        LocalDateTime lastSignal = job.getUpdatedAt() == null ? job.getCreatedAt() : job.getUpdatedAt();
        if (lastSignal != null && lastSignal.isAfter(now.minus(properties.getAbandonAfter()))) {
            return null;
        }
        PlaceImportJobStatus status = Boolean.TRUE.equals(job.getCancelRequested())
                ? PlaceImportJobStatus.CANCELLED
                : PlaceImportJobStatus.FAILED;
        return new Verdict(status, reason);
    }

    private String workerError(ScrapeJobStatusResponse worker) {
        ScrapeJobStatusResponse.ErrorDetail error = worker.getError();
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return "Scrape worker reported the job as failed";
        }
        return "Scrape worker reported: " + error.getMessage();
    }

    private String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private record Verdict(PlaceImportJobStatus status, String reason) {
    }
}
