package com.ds.goroute.job;

import com.ds.goroute.quest.service.QuestAutoPauseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Post-publish monitoring (§9): pulls quests with excessive drop-off to PAUSED. Single-instance
 * (no Redis lock), hourly by default.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestMonitoringJob {

    private final QuestAutoPauseService autoPauseService;

    @Scheduled(cron = "${goroute.jobs.quest-monitoring-cron:0 40 * * * *}")
    public void run() {
        autoPauseService.evaluateAll();
    }
}
