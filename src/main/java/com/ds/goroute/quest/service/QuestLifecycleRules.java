package com.ds.goroute.quest.service;

import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.QuestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Which operational status change and which delete a creator or an admin may make on a quest.
 * The review path (DRAFT → PENDING → IN_REVIEW → PUBLISHED | DENIED) has its own endpoints; this
 * covers only the off-states around a live quest — pause, suspend, archive and bringing it back —
 * and adds the two data guards {@link QuestStatus} leaves to the service layer.
 */
@Component
@RequiredArgsConstructor
public class QuestLifecycleRules {

    /** The states a quest is in once it has been live; only these move through the status endpoints. */
    private static final Set<QuestStatus> OPERATIONAL = EnumSet.of(
            QuestStatus.PUBLISHED, QuestStatus.PAUSED, QuestStatus.SUSPENDED, QuestStatus.ARCHIVED);

    private final BusinessConfigService config;

    /** The statuses {@code actor} may move this quest to through a status change, in enum order. */
    public List<QuestStatus> targets(Quest quest, QuestStatus.Actor actor) {
        QuestStatus from = quest.questStatus();
        if (!OPERATIONAL.contains(from)) {
            return List.of();
        }
        return EnumSet.allOf(QuestStatus.class).stream()
                .filter(OPERATIONAL::contains)
                .filter(to -> from.canTransitionTo(to, actor))
                .filter(to -> passesDataGuards(quest, to, actor))
                .toList();
    }

    public boolean canMove(Quest quest, QuestStatus target, QuestStatus.Actor actor) {
        return targets(quest, actor).contains(target);
    }

    /**
     * A creator deletes only what never went live (DRAFT, DENIED). An admin deletes anything that
     * is not live right now; a PUBLISHED quest has to be taken offline first. Open runs are
     * checked when the delete is made, not here.
     */
    public boolean isDeletable(Quest quest, QuestStatus.Actor actor) {
        QuestStatus status = quest.questStatus();
        return actor == QuestStatus.Actor.ADMIN
                ? status != QuestStatus.PUBLISHED
                : status.isCreatorEditable();
    }

    private boolean passesDataGuards(Quest quest, QuestStatus to, QuestStatus.Actor actor) {
        if (to != QuestStatus.PUBLISHED) {
            return true;
        }
        if (quest.getPublishedVersionId() == null) {
            return false;
        }
        QuestStatus from = quest.questStatus();
        // A creator lifts only their own pause; a SYSTEM or ADMIN pause is the admin's to lift.
        if (from == QuestStatus.PAUSED && actor == QuestStatus.Actor.CREATOR) {
            return QuestStatus.Actor.CREATOR.name().equals(quest.getPausedBy());
        }
        // An archived quest can come back only inside the restore window. Nothing writes to an
        // archived quest, so updated_at is the moment it was archived.
        if (from == QuestStatus.ARCHIVED) {
            int days = config.getInt(BusinessConfigKey.QUEST_ARCHIVE_RESTORE_DAYS);
            return quest.getUpdatedAt() != null
                    && !quest.getUpdatedAt().plusDays(days).isBefore(LocalDateTime.now());
        }
        return true;
    }
}
