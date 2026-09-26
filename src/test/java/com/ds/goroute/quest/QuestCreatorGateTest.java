package com.ds.goroute.quest;

import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.service.QuestCreatorGate;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QuestCreatorGate")
class QuestCreatorGateTest {

    @Mock private BusinessConfigService config;
    @Mock private BetaAccessService betaAccess;
    @Mock private QuestRepository repository;
    private QuestCreatorGate gate;

    private final UUID userId = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        gate = new QuestCreatorGate(config, betaAccess, repository);
        when(config.getInt(BusinessConfigKey.QUEST_SUBMIT_COOLDOWN_MINUTES)).thenReturn(30);
        when(config.getInt(BusinessConfigKey.QUEST_MAX_PENDING_PER_CREATOR)).thenReturn(1);
        when(config.getInt(BusinessConfigKey.QUEST_MAX_PUBLISHED_NEW_CREATOR)).thenReturn(3);
        when(config.getInt(BusinessConfigKey.QUEST_DENIED_STREAK_BLOCK)).thenReturn(3);
        when(config.getInt(BusinessConfigKey.QUEST_DENIED_BLOCK_DAYS)).thenReturn(7);
        when(config.getBoolean(BusinessConfigKey.QUEST_ENABLED)).thenReturn(true);
    }

    private QuestCreatorProfile active() {
        return QuestCreatorProfile.builder().id(creatorId).userId(userId).status("ACTIVE")
                .qualityScore(0).deniedStreak(0).build();
    }

    @Test
    @DisplayName("creation is closed when the flag is off and the user is not a beta tester")
    void closedWhenFlagOff() {
        when(config.getBoolean(BusinessConfigKey.QUEST_ENABLED)).thenReturn(false);
        when(betaAccess.isBetaUser(userId)).thenReturn(false);
        assertThatThrownBy(() -> gate.requireCreationOpen(userId))
                .isInstanceOf(BusinessException.class).hasMessageContaining("not open");
    }

    @Test
    @DisplayName("a beta tester passes even while the flag is off")
    void betaBypassesFlag() {
        when(config.getBoolean(BusinessConfigKey.QUEST_ENABLED)).thenReturn(false);
        when(betaAccess.isBetaUser(userId)).thenReturn(true);
        assertThatCode(() -> gate.requireCreationOpen(userId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a suspended creator cannot submit")
    void suspendedCannotSubmit() {
        QuestCreatorProfile suspended = active();
        suspended.setStatus("SUSPENDED");
        assertThatThrownBy(() -> gate.requireCanSubmit(suspended))
                .isInstanceOf(BusinessException.class).hasMessageContaining("suspended");
    }

    @Test
    @DisplayName("a creator inside the submit cooldown cannot submit again")
    void cooldownBlocks() {
        QuestCreatorProfile creator = active();
        creator.setLastSubmittedAt(LocalDateTime.now().minusMinutes(5));
        assertThatThrownBy(() -> gate.requireCanSubmit(creator))
                .isInstanceOf(BusinessException.class).hasMessageContaining("wait");
    }

    @Test
    @DisplayName("a creator at the pending cap cannot submit")
    void pendingCapBlocks() {
        QuestCreatorProfile creator = active();
        when(repository.countQuestsByCreatorAndStatuses(eq(creatorId), any())).thenReturn(1L);
        assertThatThrownBy(() -> gate.requireCanSubmit(creator))
                .isInstanceOf(BusinessException.class).hasMessageContaining("awaiting review");
    }

    @Test
    @DisplayName("a new creator at the published cap cannot submit")
    void newCreatorPublishedCapBlocks() {
        QuestCreatorProfile creator = active();
        when(repository.countQuestsByCreatorAndStatuses(eq(creatorId), any())).thenReturn(0L, 3L);
        assertThatThrownBy(() -> gate.requireCanSubmit(creator))
                .isInstanceOf(BusinessException.class).hasMessageContaining("published quests");
    }

    @Test
    @DisplayName("a clean creator may submit")
    void cleanCreatorPasses() {
        QuestCreatorProfile creator = active();
        when(repository.countQuestsByCreatorAndStatuses(eq(creatorId), any())).thenReturn(0L);
        assertThatCode(() -> gate.requireCanSubmit(creator)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a denial past the streak threshold blocks the creator for the configured window")
    void denialStreakBlocks() {
        QuestCreatorProfile creator = active();
        creator.setDeniedStreak(2); // this denial makes it 3 == threshold
        gate.recordDenial(creator);
        ArgumentCaptor<LocalDateTime> until = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).updateCreatorDenied(eq(creatorId), eq(3), until.capture());
        assertThat(until.getValue()).isAfter(LocalDateTime.now().plusDays(6));
    }

    @Test
    @DisplayName("an early denial extends the streak but does not block yet")
    void earlyDenialNoBlock() {
        QuestCreatorProfile creator = active();
        creator.setDeniedStreak(0);
        gate.recordDenial(creator);
        verify(repository).updateCreatorDenied(eq(creatorId), eq(1), org.mockito.ArgumentMatchers.isNull());
    }
}
