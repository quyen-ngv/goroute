package com.ds.goroute.quest.service;

import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.persistence.QuestRunRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.QuestRunStatus;
import com.ds.goroute.type.QuestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The drop-off safety valve (§3.1 auto-pause, §9). A published quest whose players keep abandoning
 * it is pulled to {@code PAUSED} by the system, and only an admin can lift a system pause — a
 * creator cannot simply switch it back on. Gated by a minimum sample so a brand-new quest with two
 * of its first five players quitting is not paused.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestAutoPauseService {

    private static final int BATCH = 500;

    private final QuestRepository questRepository;
    private final QuestRunRepository runRepository;
    private final BusinessConfigService config;

    public int evaluateAll() {
        List<Quest> quests = questRepository.findForReview(List.of(QuestStatus.PUBLISHED.name()), BATCH, 0);
        int windowHours = config.getInt(BusinessConfigKey.QUEST_AUTOPAUSE_WINDOW_HOURS);
        int minRuns = config.getInt(BusinessConfigKey.QUEST_AUTOPAUSE_MIN_RUNS);
        int dropoffPct = config.getInt(BusinessConfigKey.QUEST_AUTOPAUSE_DROPOFF_PCT);
        LocalDateTime since = LocalDateTime.now().minusHours(windowHours);
        int paused = 0;
        for (Quest quest : quests) {
            try {
                if (evaluateOne(quest, since, minRuns, dropoffPct)) {
                    paused++;
                }
            } catch (RuntimeException ex) {
                log.error("Auto-pause evaluation failed for quest {}: {}", quest.getId(), ex.getMessage());
            }
        }
        if (paused > 0) {
            log.warn("Auto-paused {} quests for excessive drop-off", paused);
        }
        return paused;
    }

    @Transactional
    public boolean evaluateOne(Quest quest, LocalDateTime since, int minRuns, int dropoffPct) {
        int completed = runRepository.countRunsByStatusSince(quest.getId(), QuestRunStatus.COMPLETED.name(), since);
        int abandoned = runRepository.countRunsByStatusSince(quest.getId(), QuestRunStatus.ABANDONED.name(), since);
        int total = completed + abandoned;
        if (total < minRuns) {
            return false;
        }
        int abandonRate = abandoned * 100 / total;
        if (abandonRate < dropoffPct) {
            return false;
        }
        boolean paused = questRepository.updateStatus(quest.getId(), quest.getDataVersion(),
                QuestStatus.PAUSED.name(), QuestStatus.Actor.SYSTEM.name(), LocalDateTime.now());
        if (paused) {
            log.warn("Quest {} auto-paused: {}% abandon over {} runs", quest.getId(), abandonRate, total);
        }
        return paused;
    }
}
