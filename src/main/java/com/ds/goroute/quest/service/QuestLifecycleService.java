package com.ds.goroute.quest.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.domain.QuestVersion;
import com.ds.goroute.quest.dto.QuestAdminListItem;
import com.ds.goroute.quest.dto.QuestStatusChangeRequest;
import com.ds.goroute.quest.dto.QuestStatusChangeResponse;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.persistence.QuestRunRepository;
import com.ds.goroute.type.QuestCreatorStatus;
import com.ds.goroute.type.QuestRunStatus;
import com.ds.goroute.type.QuestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Managing a quest after it has been live: the creator pauses, archives or resumes their own; an
 * admin additionally suspends, lifts any pause, restores from the archive, lists every quest and
 * deletes. Which move is allowed is {@link QuestLifecycleRules}; this class adds ownership, the
 * optimistic lock and the open-run guard on delete.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestLifecycleService {

    private static final int MAX_PAGE_SIZE = 50;

    private final QuestRepository repository;
    private final QuestRunRepository runRepository;
    private final QuestLifecycleRules rules;

    @Transactional
    public QuestStatusChangeResponse changeStatusAsCreator(UUID questId, UUID actorUserId,
                                                           QuestStatusChangeRequest request) {
        Quest quest = requireQuest(questId);
        QuestCreatorProfile creator = repository.findCreatorById(quest.getCreatorId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quest not found"));
        // 404 to anyone else: a guessed id must not confirm a quest exists.
        if (!creator.getUserId().equals(actorUserId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Quest not found");
        }
        if (creator.creatorStatus() == QuestCreatorStatus.SUSPENDED) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Your creator account is suspended");
        }
        return move(quest, request, QuestStatus.Actor.CREATOR);
    }

    @Transactional
    public QuestStatusChangeResponse changeStatusAsAdmin(UUID questId, QuestStatusChangeRequest request) {
        return move(requireQuest(questId), request, QuestStatus.Actor.ADMIN);
    }

    /**
     * Soft-deletes any quest that is not live. A run in progress pins the quest's version and
     * looks the quest up on every step, so a quest with one cannot go until the run ends.
     */
    @Transactional
    public void deleteAsAdmin(UUID questId, Long expectedVersion) {
        Quest quest = requireQuest(questId);
        if (!rules.isDeletable(quest, QuestStatus.Actor.ADMIN)) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                    "Take a published quest offline (pause, suspend or archive) before deleting it");
        }
        if (runRepository.countRunsByStatus(questId, QuestRunStatus.IN_PROGRESS.name()) > 0) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                    "Players are still running this quest; delete it after their runs end");
        }
        if (!repository.softDeleteQuest(questId, lockValue(quest, expectedVersion), LocalDateTime.now())) {
            throw conflict(questId);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestAdminListItem> listAll(QuestStatus status, int page, int size) {
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int safePage = Math.max(0, page);
        String statusFilter = status == null ? null : status.name();
        List<QuestAdminListItem> items = repository.findAllQuests(statusFilter, safeSize, safePage * safeSize)
                .stream().map(this::toAdminItem).toList();
        return PageResponse.of(items, repository.countAllQuests(statusFilter), safePage, safeSize);
    }

    private QuestStatusChangeResponse move(Quest quest, QuestStatusChangeRequest request, QuestStatus.Actor actor) {
        QuestStatus target = request.status();
        if (quest.questStatus() == target) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "Quest is already " + target);
        }
        if (!rules.canMove(quest, target, actor)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Cannot move a " + quest.getStatus() + " quest to " + target);
        }
        long expected = lockValue(quest, request.expectedVersion());
        // Who paused is what later decides who may lift it; every other state clears it.
        String pausedBy = target == QuestStatus.PAUSED ? actor.name() : null;
        LocalDateTime now = LocalDateTime.now();
        if (!repository.updateStatus(quest.getId(), expected, target.name(), pausedBy, now)) {
            throw conflict(quest.getId());
        }
        Quest moved = Quest.builder()
                .id(quest.getId())
                .status(target.name())
                .publishedVersionId(quest.getPublishedVersionId())
                .pausedBy(pausedBy)
                .dataVersion(expected + 1)
                .updatedAt(now)
                .build();
        return new QuestStatusChangeResponse(quest.getId(), target.name(), expected + 1,
                rules.targets(moved, actor).stream().map(Enum::name).toList(),
                rules.isDeletable(moved, actor));
    }

    private QuestAdminListItem toAdminItem(Quest quest) {
        UUID versionId = quest.getDraftVersionId() != null ? quest.getDraftVersionId() : quest.getPublishedVersionId();
        QuestVersion version = versionId == null ? null : repository.findVersionById(versionId).orElse(null);
        return new QuestAdminListItem(
                quest.getId(), quest.getOrigin(), quest.getStatus(),
                version == null ? null : version.getTitle(),
                version == null ? null : version.getCoverUrl(),
                quest.getCreatorId(),
                versionId == null ? 0 : repository.countCheckpoints(versionId),
                quest.getPausedBy(), quest.isPendingChangeReview(),
                quest.getDataVersion() == null ? 0 : quest.getDataVersion(), quest.getUpdatedAt(),
                rules.targets(quest, QuestStatus.Actor.ADMIN).stream().map(Enum::name).toList(),
                rules.isDeletable(quest, QuestStatus.Actor.ADMIN));
    }

    private static long lockValue(Quest quest, Long expectedVersion) {
        return expectedVersion != null && expectedVersion > 0 ? expectedVersion : quest.getDataVersion();
    }

    private Quest requireQuest(UUID questId) {
        return repository.findQuestById(questId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quest not found"));
    }

    private BusinessException conflict(UUID questId) {
        log.debug("Quest {} changed under a concurrent status write", questId);
        return new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                "This quest changed elsewhere. Reload and try again.");
    }
}
