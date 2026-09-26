package com.ds.goroute.quest.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.QuestCreatorStatus;
import com.ds.goroute.type.QuestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The throttles that stand in for the up-front human gate D4 removed (§3.2, §8). Opening creation
 * to everyone means the review queue is protected by these alone: the feature flag, a per-creator
 * pending cap, a submission cooldown, a new-creator published cap, and a denial-streak block.
 *
 * <p>The flag is checked here on the server, never trusted from the client, and beta testers pass
 * even while it is off — the standing rule for every flagged feature.
 */
@Component
@RequiredArgsConstructor
public class QuestCreatorGate {

    private static final List<String> OPEN_REVIEW =
            List.of(QuestStatus.PENDING.name(), QuestStatus.IN_REVIEW.name(), QuestStatus.FIELD_TEST.name());

    private final BusinessConfigService config;
    private final BetaAccessService betaAccess;
    private final QuestRepository repository;

    /** Creation is open once the flag is on, and to beta testers before that. */
    public void requireCreationOpen(UUID userId) {
        if (!config.getBoolean(BusinessConfigKey.QUEST_ENABLED) && !betaAccess.isBetaUser(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Quest creation is not open yet");
        }
    }

    /** Everything that must hold before a creator may submit a quest for review. */
    public void requireCanSubmit(QuestCreatorProfile creator) {
        requireCreationOpen(creator.getUserId());
        if (creator.creatorStatus() == QuestCreatorStatus.SUSPENDED) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Your creator account is suspended");
        }
        LocalDateTime now = LocalDateTime.now();
        if (creator.getBlockedUntil() != null && now.isBefore(creator.getBlockedUntil())) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "Too many quests were denied recently; try again later");
        }
        LocalDateTime last = creator.getLastSubmittedAt();
        int cooldown = config.getInt(BusinessConfigKey.QUEST_SUBMIT_COOLDOWN_MINUTES);
        if (last != null && now.isBefore(last.plusMinutes(cooldown))) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                    "Please wait before submitting another quest");
        }
        long pending = repository.countQuestsByCreatorAndStatuses(creator.getId(), OPEN_REVIEW);
        if (pending >= config.getInt(BusinessConfigKey.QUEST_MAX_PENDING_PER_CREATOR)) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                    "You already have a quest awaiting review");
        }
        boolean newCreator = creator.getQualityScore() == null || creator.getQualityScore() <= 0;
        if (newCreator) {
            long published = repository.countQuestsByCreatorAndStatuses(
                    creator.getId(), List.of(QuestStatus.PUBLISHED.name()));
            if (published >= config.getInt(BusinessConfigKey.QUEST_MAX_PUBLISHED_NEW_CREATOR)) {
                throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                        "New creators can have only a few published quests; build your quality score first");
            }
        }
    }

    public void recordSubmission(QuestCreatorProfile creator) {
        repository.updateCreatorSubmission(creator.getId(), LocalDateTime.now());
    }

    /** On a denial, extend the streak and, past the threshold, block submissions for a window (§3.2). */
    public void recordDenial(QuestCreatorProfile creator) {
        int streak = (creator.getDeniedStreak() == null ? 0 : creator.getDeniedStreak()) + 1;
        LocalDateTime blockedUntil = null;
        if (streak >= config.getInt(BusinessConfigKey.QUEST_DENIED_STREAK_BLOCK)) {
            blockedUntil = LocalDateTime.now().plusDays(config.getInt(BusinessConfigKey.QUEST_DENIED_BLOCK_DAYS));
        }
        repository.updateCreatorDenied(creator.getId(), streak, blockedUntil);
    }

    public void recordPublish(UUID creatorId) {
        repository.resetCreatorDenied(creatorId);
    }
}
