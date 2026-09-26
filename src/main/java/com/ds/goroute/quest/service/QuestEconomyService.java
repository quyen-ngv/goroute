package com.ds.goroute.quest.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.domain.QuestEarning;
import com.ds.goroute.quest.domain.QuestEntitlement;
import com.ds.goroute.quest.domain.QuestTip;
import com.ds.goroute.quest.domain.QuestVersion;
import com.ds.goroute.quest.dto.QuestEntitlementResponse;
import com.ds.goroute.quest.dto.QuestTipRequest;
import com.ds.goroute.quest.persistence.QuestEconomyMapper;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.persistence.QuestRunRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.QuestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The Stars economy for quests (§3.5, D19, D20). Unlocking spends Stars and books the creator's
 * cut into {@code quest_earnings} (never the wallet); tipping does the same. Every credit carries a
 * unique {@code source_reference} so a retried payment cannot double-credit the creator.
 */
@Service
@RequiredArgsConstructor
public class QuestEconomyService {

    private final QuestRepository questRepository;
    private final QuestRunRepository runRepository;
    private final QuestEconomyMapper economyMapper;
    private final StarService starService;
    private final BusinessConfigService config;

    @Transactional
    public QuestEntitlementResponse unlock(UUID userId, UUID questId) {
        Quest quest = publishedQuest(questId);
        if (runRepository.findEntitlement(questId, userId).isPresent()) {
            return new QuestEntitlementResponse(questId, "OWNED");
        }
        QuestCreatorProfile creator = questRepository.findCreatorById(quest.getCreatorId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quest creator missing"));
        int price = priceOf(quest);

        // The creator plays their own quest free; a free quest is free for everyone.
        if (creator.getUserId().equals(userId) || price <= 0) {
            grantEntitlement(questId, userId, "FREE", "free:" + questId + ":" + userId);
            return new QuestEntitlementResponse(questId, "FREE");
        }

        String reference = "quest_unlock:" + questId + ":" + userId;
        starService.spend(userId, price, "QUEST_UNLOCK", reference, "Unlock quest");
        grantEntitlement(questId, userId, "STARS", reference);

        int sharePct = config.getInt(BusinessConfigKey.QUEST_CREATOR_REVENUE_SHARE_PCT);
        int share = price * sharePct / 100;
        creditCreator(creator.getId(), questId, null, userId, "SALE", share, "sale:" + questId + ":" + userId);
        return new QuestEntitlementResponse(questId, "STARS");
    }

    @Transactional
    public void tip(UUID userId, UUID questId, QuestTipRequest request) {
        int amount = request.getAmount() == null ? 0 : request.getAmount();
        int min = config.getInt(BusinessConfigKey.QUEST_TIP_MIN_STARS);
        int max = config.getInt(BusinessConfigKey.QUEST_TIP_MAX_STARS);
        if (amount < min || amount > max) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "A tip must be between " + min + " and " + max + " Stars");
        }
        Quest quest = publishedQuest(questId);
        QuestCreatorProfile creator = questRepository.findCreatorById(quest.getCreatorId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quest creator missing"));
        if (creator.getUserId().equals(userId)) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "You cannot tip your own quest");
        }

        UUID tipId = UUID.randomUUID();
        starService.spend(userId, amount, "QUEST_TIP", "quest_tip:" + tipId, "Tip to quest creator");
        economyMapper.insertTip(QuestTip.builder()
                .id(tipId).questId(questId).runId(request.getRunId()).fromUserId(userId)
                .toCreatorId(creator.getId()).amount(amount).fundingSource("STARS")
                .message(request.getMessage()).messagePublic(request.isMessagePublic())
                .createdAt(LocalDateTime.now()).build());

        int cutPct = config.getInt(BusinessConfigKey.QUEST_TIP_PLATFORM_CUT_PCT);
        int net = amount - (amount * cutPct / 100);
        creditCreator(creator.getId(), questId, request.getRunId(), userId, "TIP", net, "tip:" + tipId);
    }

    // --- helpers -----------------------------------------------------------------------

    private void creditCreator(UUID creatorId, UUID questId, UUID runId, UUID fromUserId,
                               String source, int amount, String reference) {
        if (amount <= 0 || economyMapper.findEarningByReference(reference) != null) {
            return;
        }
        int settleDays = config.getInt(BusinessConfigKey.QUEST_EARNING_SETTLE_DAYS);
        economyMapper.insertEarning(QuestEarning.builder()
                .id(UUID.randomUUID()).creatorId(creatorId).questId(questId).runId(runId)
                .fromUserId(fromUserId).source(source).amount(amount).fundingSource("STARS")
                .status("PENDING").settleAt(LocalDateTime.now().plusDays(settleDays))
                .sourceReference(reference).createdAt(LocalDateTime.now()).build());
    }

    private void grantEntitlement(UUID questId, UUID userId, String funding, String reference) {
        if (runRepository.findEntitlement(questId, userId).isPresent()) {
            return;
        }
        runRepository.insertEntitlement(QuestEntitlement.builder()
                .id(UUID.randomUUID()).questId(questId).userId(userId).fundingSource(funding)
                .sourceReference(reference).createdAt(LocalDateTime.now()).build());
    }

    private Quest publishedQuest(UUID questId) {
        Quest quest = questRepository.findQuestById(questId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quest not found"));
        if (quest.questStatus() != QuestStatus.PUBLISHED || quest.getPublishedVersionId() == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Quest not found");
        }
        return quest;
    }

    private int priceOf(Quest quest) {
        return questRepository.findVersionById(quest.getPublishedVersionId())
                .map(QuestVersion::getPriceStars)
                .map(p -> p == null ? 0 : p)
                .orElse(0);
    }
}
