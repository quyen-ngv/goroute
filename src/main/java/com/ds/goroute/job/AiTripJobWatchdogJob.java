package com.ds.goroute.job;

import com.ds.goroute.mapper.AiTripGenerationMapper;
import com.ds.goroute.service.AiTripGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Fails AI trip jobs whose worker went quiet (crash, restart, lost callback), so the
 * user's quota is released and the app stops resuming a job that will never finish.
 *
 * <p>Safe to run repeatedly: {@code fail} is conditional on the job not being terminal,
 * so a commit or event that lands in the same moment always wins.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiTripJobWatchdogJob {

    private final AiTripGenerationMapper mapper;
    private final AiTripGenerationService service;

    @Value("${goroute.jobs.ai-trip-stale-minutes:20}")
    private int staleMinutes;

    @Scheduled(fixedDelayString = "${goroute.jobs.ai-trip-watchdog-delay-ms:300000}")
    public void failStalledJobs() {
        try {
            List<UUID> stale = mapper.findStaleActiveIds(staleMinutes);
            for (UUID jobId : stale) {
                log.warn("AI trip job {} has been silent for over {} minutes; marking FAILED", jobId, staleMinutes);
                service.fail(jobId, "AI generation timed out");
            }
        } catch (Exception exception) {
            log.error("AI trip watchdog run failed: {}", exception.getMessage(), exception);
        }
    }
}
