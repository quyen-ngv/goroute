package com.ds.goroute.quest;

import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.domain.QuestEntitlement;
import com.ds.goroute.quest.domain.QuestVersion;
import com.ds.goroute.quest.dto.QuestTipRequest;
import com.ds.goroute.quest.persistence.QuestEconomyMapper;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.persistence.QuestRunRepository;
import com.ds.goroute.quest.service.QuestEconomyService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.type.BusinessConfigKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QuestEconomyService")
class QuestEconomyServiceTest {

    @Mock private QuestRepository questRepository;
    @Mock private QuestRunRepository runRepository;
    @Mock private QuestEconomyMapper economyMapper;
    @Mock private StarService starService;
    @Mock private BusinessConfigService config;
    private QuestEconomyService service;

    private final UUID questId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();
    private final UUID creatorUser = UUID.randomUUID();
    private final UUID buyer = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new QuestEconomyService(questRepository, runRepository, economyMapper, starService, config);
        when(config.getInt(BusinessConfigKey.QUEST_CREATOR_REVENUE_SHARE_PCT)).thenReturn(70);
        when(config.getInt(BusinessConfigKey.QUEST_EARNING_SETTLE_DAYS)).thenReturn(14);
        when(config.getInt(BusinessConfigKey.QUEST_TIP_MIN_STARS)).thenReturn(5);
        when(config.getInt(BusinessConfigKey.QUEST_TIP_MAX_STARS)).thenReturn(500);
        when(config.getInt(BusinessConfigKey.QUEST_TIP_PLATFORM_CUT_PCT)).thenReturn(0);
        when(questRepository.findCreatorById(creatorId)).thenReturn(Optional.of(
                QuestCreatorProfile.builder().id(creatorId).userId(creatorUser).build()));
        when(runRepository.findEntitlement(eq(questId), any())).thenReturn(Optional.empty());
    }

    private Quest published() {
        return Quest.builder().id(questId).creatorId(creatorId).status("PUBLISHED")
                .publishedVersionId(versionId).build();
    }

    private void price(int stars) {
        when(questRepository.findQuestById(questId)).thenReturn(Optional.of(published()));
        when(questRepository.findVersionById(versionId))
                .thenReturn(Optional.of(QuestVersion.builder().id(versionId).priceStars(stars).build()));
    }

    @Test
    @DisplayName("a paid unlock spends Stars and books the creator's revenue share to earnings")
    void paidUnlockCreditsCreator() {
        price(100);

        var result = service.unlock(buyer, questId);

        assertThat(result.fundingSource()).isEqualTo("STARS");
        verify(starService).spend(eq(buyer), eq(100), eq("QUEST_UNLOCK"),
                eq("quest_unlock:" + questId + ":" + buyer), any());
        verify(runRepository).insertEntitlement(any(QuestEntitlement.class));
        var earning = ArgumentCaptor.forClass(com.ds.goroute.quest.domain.QuestEarning.class);
        verify(economyMapper).insertEarning(earning.capture());
        assertThat(earning.getValue().getAmount()).isEqualTo(70); // 70% of 100
        assertThat(earning.getValue().getSource()).isEqualTo("SALE");
        assertThat(earning.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("a free quest grants a FREE entitlement without spending")
    void freeUnlockNoSpend() {
        price(0);

        var result = service.unlock(buyer, questId);

        assertThat(result.fundingSource()).isEqualTo("FREE");
        verify(starService, never()).spend(any(), anyInt(), any(), any(), any());
        verify(economyMapper, never()).insertEarning(any());
    }

    @Test
    @DisplayName("the creator unlocks their own quest for free")
    void creatorUnlocksFree() {
        price(100);

        var result = service.unlock(creatorUser, questId);

        assertThat(result.fundingSource()).isEqualTo("FREE");
        verify(starService, never()).spend(any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("an already-owned quest does not spend again")
    void alreadyOwned() {
        price(100);
        when(runRepository.findEntitlement(eq(questId), eq(buyer)))
                .thenReturn(Optional.of(QuestEntitlement.builder().id(UUID.randomUUID()).build()));

        var result = service.unlock(buyer, questId);

        assertThat(result.fundingSource()).isEqualTo("OWNED");
        verify(starService, never()).spend(any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("a tip books the net amount to the creator's earnings, not their wallet")
    void tipCreditsEarnings() {
        when(questRepository.findQuestById(questId)).thenReturn(Optional.of(published()));
        QuestTipRequest request = new QuestTipRequest();
        request.setAmount(50);

        service.tip(buyer, questId, request);

        verify(starService).spend(eq(buyer), eq(50), eq("QUEST_TIP"), any(), any());
        verify(economyMapper).insertTip(any());
        var earning = ArgumentCaptor.forClass(com.ds.goroute.quest.domain.QuestEarning.class);
        verify(economyMapper).insertEarning(earning.capture());
        assertThat(earning.getValue().getSource()).isEqualTo("TIP");
        assertThat(earning.getValue().getAmount()).isEqualTo(50);
    }

    @Test
    @DisplayName("a tip below the minimum is refused")
    void tipTooSmall() {
        when(questRepository.findQuestById(questId)).thenReturn(Optional.of(published()));
        QuestTipRequest request = new QuestTipRequest();
        request.setAmount(1);

        assertThatThrownBy(() -> service.tip(buyer, questId, request))
                .isInstanceOf(BusinessException.class).hasMessageContaining("between");
        verify(starService, never()).spend(any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("tipping your own quest is refused")
    void tipSelf() {
        when(questRepository.findQuestById(questId)).thenReturn(Optional.of(published()));
        QuestTipRequest request = new QuestTipRequest();
        request.setAmount(50);

        assertThatThrownBy(() -> service.tip(creatorUser, questId, request))
                .isInstanceOf(BusinessException.class).hasMessageContaining("your own");
    }
}
