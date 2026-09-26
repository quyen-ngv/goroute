package com.ds.goroute.job;

import com.ds.goroute.quest.service.QuestFieldTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes field-test quests that have reached their completion threshold (§8 phase 6). Runs on a
 * single instance (the app has no Redis job lock), hourly by default.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestFieldTestEvaluationJob {

    private final QuestFieldTestService fieldTestService;

    @Scheduled(cron = "${goroute.jobs.quest-field-test-cron:0 20 * * * *}")
    public void run() {
        fieldTestService.evaluateAll();
    }
}
