package com.ds.goroute.quest.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.domain.QuestReviewComment;
import com.ds.goroute.quest.domain.QuestReviewDecisionRow;
import com.ds.goroute.quest.dto.QuestReviewDecisionRequest;
import com.ds.goroute.quest.dto.QuestReviewQueueItem;
import com.ds.goroute.quest.dto.QuestReviewResultResponse;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.domain.QuestVersion;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.ds.goroute.type.QuestCreatorStatus;
import com.ds.goroute.type.QuestOrigin;
import com.ds.goroute.type.QuestReviewDecision;
import com.ds.goroute.type.QuestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The review path on the console (§3.1, §3.3). The decision to publish IS the publish; there is no
 * separate step. A reviewer may not review their own quest, with one audited exception: a
 * SUPER_ADMIN reviewing a SYSTEM quest, recorded as {@code self_approved} (§3.3 layer 2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestReviewService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final List<String> QUEUE_STATUSES =
            List.of(QuestStatus.PENDING.name(), QuestStatus.IN_REVIEW.name(), QuestStatus.FIELD_TEST.name());

    /** The checklist key the console sets once every recording in the quest has been played. */
    static final String AUDIO_HEARD_CHECK = "audioListened";

    private final QuestRepository repository;
    private final QuestCreatorGate creatorGate;
    private final MarketplaceJson json;

    @Transactional(readOnly = true)
    public PageResponse<QuestReviewQueueItem> queue(int page, int size) {
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int safePage = Math.max(0, page);
        List<QuestReviewQueueItem> items = repository.findForReview(QUEUE_STATUSES, safeSize, safePage * safeSize)
                .stream().map(this::toQueueItem).toList();
        return PageResponse.of(items, repository.countForReview(QUEUE_STATUSES), safePage, safeSize);
    }

    @Transactional
    public QuestReviewResultResponse openForReview(UUID questId, Long expectedVersion) {
        Quest quest = requireQuest(questId);
        if (quest.questStatus() != QuestStatus.PENDING) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "Quest is not pending review");
        }
        long expected = expectedVersion != null ? expectedVersion : quest.getDataVersion();
        if (!repository.updateStatus(questId, expected, QuestStatus.IN_REVIEW.name(), null, LocalDateTime.now())) {
            throw conflict(questId);
        }
        return new QuestReviewResultResponse(questId, QuestStatus.IN_REVIEW.name(), false);
    }

    @Transactional
    public QuestReviewResultResponse decide(UUID questId, UUID reviewerUserId, boolean isSuperAdmin,
                                            QuestReviewDecisionRequest request) {
        Quest quest = requireQuest(questId);
        QuestStatus from = quest.questStatus();
        if (from != QuestStatus.IN_REVIEW && from != QuestStatus.FIELD_TEST) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "Quest is not in review");
        }
        QuestReviewDecision decision = parseDecision(request.decision());
        QuestStatus target = decision.targetStatus();
        if (!from.canTransitionTo(target, QuestStatus.Actor.ADMIN)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Cannot " + decision + " a quest that is " + from);
        }

        QuestCreatorProfile creator = repository.findCreatorById(quest.getCreatorId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quest creator missing"));
        boolean selfApproved = resolveSelfApproval(quest, creator, reviewerUserId, isSuperAdmin);
        if (decision == QuestReviewDecision.PUBLISHED) {
            requireAudioHeard(quest, request.checklist());
        }

        long expected = request.expectedVersion() != null ? request.expectedVersion() : quest.getDataVersion();
        UUID reviewedVersionId = quest.getDraftVersionId();
        LocalDateTime now = LocalDateTime.now();

        boolean ok = decision == QuestReviewDecision.PUBLISHED
                ? repository.updatePublish(questId, expected, reviewedVersionId, selfApproved, now)
                : repository.updateStatus(questId, expected, target.name(), null, now);
        if (!ok) {
            throw conflict(questId);
        }

        repository.insertReviewDecision(QuestReviewDecisionRow.builder()
                .id(UUID.randomUUID()).questId(questId).questVersionId(reviewedVersionId)
                .reviewerUserId(reviewerUserId).decision(decision.name()).reason(request.reason())
                .checklist(json.write(request.checklist() == null ? Map.of() : request.checklist()))
                .selfApproved(selfApproved).createdAt(now).build());

        if (request.comments() != null) {
            for (QuestReviewDecisionRequest.CheckpointComment c : request.comments()) {
                if (c.comment() == null || c.comment().isBlank()) {
                    continue;
                }
                repository.insertReviewComment(QuestReviewComment.builder()
                        .id(UUID.randomUUID()).questId(questId).questVersionId(reviewedVersionId)
                        .checkpointId(c.checkpointId()).reviewerUserId(reviewerUserId)
                        .comment(c.comment()).createdAt(now).build());
            }
        }

        // The denial streak that blocks a creator who keeps submitting bad quests (§3.2); a publish
        // resets it. Field test is neither a pass nor a fail, so it leaves the streak untouched.
        if (decision == QuestReviewDecision.DENIED) {
            creatorGate.recordDenial(creator);
        } else if (decision == QuestReviewDecision.PUBLISHED) {
            creatorGate.recordPublish(creator.getId());
        }
        return new QuestReviewResultResponse(questId, target.name(), selfApproved);
    }

    @Transactional(readOnly = true)
    public List<QuestReviewDecisionRow> decisions(UUID questId) {
        return repository.findDecisionsByQuest(questId);
    }

    @Transactional(readOnly = true)
    public List<QuestReviewComment> comments(UUID questId) {
        return repository.findCommentsByQuest(questId);
    }

    /**
     * Clears the post-review flag of a live edit once a reviewer has looked at it (§3.14.3). There
     * is no filter for audio, so this is the person's "I listened" for a recording that went out
     * with an edit.
     */
    @Transactional
    public QuestReviewResultResponse acknowledgeLiveEdit(UUID questId) {
        Quest quest = requireQuest(questId);
        if (!quest.isPendingChangeReview()) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "This quest has no edit waiting to be seen");
        }
        if (!repository.clearPendingChangeReview(questId, LocalDateTime.now())) {
            throw conflict(questId);
        }
        return new QuestReviewResultResponse(questId, quest.getStatus(), false);
    }

    /**
     * No machine filters audio (§3.14.3): a quest with any recording is published only when the
     * reviewer confirms, in the checklist, that they listened to every one. The console ticks this
     * only once each clip has been played.
     */
    private void requireAudioHeard(Quest quest, Map<String, Object> checklist) {
        QuestVersion version = repository.loadVersionGraph(quest.getDraftVersionId()).orElse(null);
        if (version == null) {
            return;
        }
        boolean hasAudio = version.getCheckpoints().stream().anyMatch(cp ->
                cp.getStoryAudioUrl() != null
                        || cp.getStops().stream().anyMatch(stop -> stop.getAudioUrl() != null));
        if (hasAudio && (checklist == null || !Boolean.TRUE.equals(checklist.get(AUDIO_HEARD_CHECK)))) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Listen to every recording before publishing");
        }
    }

    // --- helpers -----------------------------------------------------------------------

    /** Suspend or reactivate a creator (§3.2). A SUSPENDED creator's quests fall out of discovery
     *  because the public gate requires {@code quest_creators.status = 'ACTIVE'} (§6.3). */
    @Transactional
    public void setCreatorStatus(UUID creatorId, String status) {
        QuestCreatorStatus parsed;
        try {
            parsed = QuestCreatorStatus.valueOf(status);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Unknown creator status: " + status);
        }
        repository.findCreatorById(creatorId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Creator not found"));
        repository.updateCreatorStatus(creatorId, parsed.name());
    }

    private boolean resolveSelfApproval(Quest quest, QuestCreatorProfile creator, UUID reviewerUserId,
                                        boolean isSuperAdmin) {
        if (!creator.getUserId().equals(reviewerUserId)) {
            return false;
        }
        // Self-review: only a SUPER_ADMIN on a SYSTEM quest, and it is recorded (§3.3 layer 2).
        if (isSuperAdmin && quest.questOrigin() == QuestOrigin.SYSTEM) {
            return true;
        }
        throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                "A reviewer may not review their own quest");
    }

    private QuestReviewDecision parseDecision(String value) {
        try {
            return QuestReviewDecision.valueOf(value);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Unknown decision: " + value);
        }
    }

    private QuestReviewQueueItem toQueueItem(Quest quest) {
        String title = quest.getDraftVersionId() == null ? null
                : repository.findVersionById(quest.getDraftVersionId()).map(QuestVersion::getTitle).orElse(null);
        return new QuestReviewQueueItem(quest.getId(), quest.getOrigin(), quest.getStatus(), title,
                quest.getCreatorId(), quest.getDataVersion() == null ? 0 : quest.getDataVersion(),
                quest.getUpdatedAt(), quest.isPendingChangeReview());
    }

    private Quest requireQuest(UUID questId) {
        return repository.findQuestById(questId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quest not found"));
    }

    private BusinessException conflict(UUID questId) {
        log.debug("Quest {} changed under a concurrent review write", questId);
        return new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                "This quest changed elsewhere. Reload and try again.");
    }
}
