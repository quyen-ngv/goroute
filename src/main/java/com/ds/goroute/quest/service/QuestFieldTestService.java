package com.ds.goroute.quest.service;

import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.persistence.QuestRunRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.QuestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Field test (§8, phase 6). A reviewer may send a quest to {@code FIELD_TEST} instead of
 * publishing outright; once enough real players have completed the field-test version, the system
 * publishes it. This is the auto-graduation the {@code QuestFieldTestEvaluationJob} drives.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestFieldTestService {

    private static final int BATCH = 200;

    private final QuestRepository questRepository;
    private final QuestRunRepository runRepository;
    private final BusinessConfigService config;

    /** Publishes every field-test quest that has reached the completion threshold. Returns the count. */
    public int evaluateAll() {
        List<Quest> quests = questRepository.findForReview(List.of(QuestStatus.FIELD_TEST.name()), BATCH, 0);
        int required = config.getInt(BusinessConfigKey.QUEST_FIELD_TEST_COMPLETIONS);
        int published = 0;
        for (Quest quest : quests) {
            try {
                if (evaluateOne(quest, required)) {
                    published++;
                }
            } catch (RuntimeException ex) {
                log.error("Field-test evaluation failed for quest {}: {}", quest.getId(), ex.getMessage());
            }
        }
        if (published > 0) {
            log.info("Field test published {} of {} quests", published, quests.size());
        }
        return published;
    }

    /** Its own transaction so one bad quest does not abort the batch. */
    @Transactional
    public boolean evaluateOne(Quest quest, int required) {
        if (quest.getDraftVersionId() == null) {
            return false;
        }
        int completions = runRepository.countCompletedRunsForVersion(quest.getId(), quest.getDraftVersionId());
        if (completions < required) {
            return false;
        }
        boolean ok = questRepository.updatePublish(quest.getId(), quest.getDataVersion(),
                quest.getDraftVersionId(), false, LocalDateTime.now());
        if (ok) {
            log.info("Quest {} graduated from field test after {} completions", quest.getId(), completions);
        }
        return ok;
    }
}
