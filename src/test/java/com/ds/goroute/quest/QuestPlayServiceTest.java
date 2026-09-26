package com.ds.goroute.quest;

import com.ds.goroute.entity.PassportEvent;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.PassportMapper;
import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.domain.QuestCheckpoint;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.domain.QuestQuestion;
import com.ds.goroute.quest.domain.QuestRun;
import com.ds.goroute.quest.domain.QuestRunCheckpoint;
import com.ds.goroute.quest.domain.QuestRunMember;
import com.ds.goroute.quest.domain.QuestRunQuestion;
import com.ds.goroute.quest.domain.QuestVersion;
import com.ds.goroute.quest.dto.QuestAnswerRequest;
import com.ds.goroute.quest.dto.QuestArrivalResponse;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.persistence.QuestRunRepository;
import com.ds.goroute.quest.service.QuestAnswerGrader;
import com.ds.goroute.quest.service.QuestPlayService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.service.checkin.CheckinVerifier;
import com.ds.goroute.service.checkin.VerificationTarget;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.ds.goroute.type.BusinessConfigKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QuestPlayService")
class QuestPlayServiceTest {

    @Mock private QuestRepository questRepository;
    @Mock private QuestRunRepository runRepository;
    @Mock private CheckinVerifier checkinVerifier;
    @Mock private BusinessConfigService config;
    @Mock private StarService starService;
    @Mock private PassportMapper passportMapper;

    private QuestPlayService service;

    private final UUID user = UUID.randomUUID();
    private final UUID questId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final UUID runId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();
    private final UUID cpId = UUID.randomUUID();
    private final UUID qId = UUID.randomUUID();

    private QuestRun run;
    private QuestRunMember member;

    @BeforeEach
    void setUp() {
        service = new QuestPlayService(questRepository, runRepository, checkinVerifier,
                new QuestAnswerGrader(), config, starService, passportMapper, new MarketplaceJson(new ObjectMapper()));

        when(config.getInt(BusinessConfigKey.QUEST_ARRIVAL_STABLE_SAMPLES)).thenReturn(1);
        when(config.getInt(BusinessConfigKey.QUEST_UNLOCK_RADIUS_METERS)).thenReturn(40);
        when(config.getInt(BusinessConfigKey.QUEST_MAX_GUESS_ATTEMPTS)).thenReturn(5);
        when(config.getInt(BusinessConfigKey.QUEST_ARRIVAL_CLIENT_INTERVAL_SECONDS)).thenReturn(5);
        when(config.getInt(BusinessConfigKey.QUEST_GROUP_PRESENCE_PCT)).thenReturn(70);

        run = QuestRun.builder().id(runId).questId(questId).questVersionId(versionId)
                .ownerUserId(user).status("IN_PROGRESS").dataVersion(1L).build();
        member = QuestRunMember.builder().id(memberId).runId(runId).userId(user)
                .verification("UNVERIFIED").presenceCheckpoints(0).build();

        when(runRepository.findRunById(runId)).thenReturn(Optional.of(run));
        when(runRepository.findMember(runId, user)).thenReturn(Optional.of(member));
        when(runRepository.findMembers(runId)).thenReturn(List.of(member));
        when(questRepository.loadVersionGraph(versionId)).thenReturn(Optional.of(version(50)));
    }

    private QuestVersion version(int rewardStars) {
        QuestQuestion q = QuestQuestion.builder().id(qId).sortOrder(0).required(true).bonus(false)
                .type("TEXT").prompt("City?").answerPlain("Hà Nội").choices(List.of()).build();
        QuestCheckpoint cp = QuestCheckpoint.builder().id(cpId).sortOrder(0).name("Gate")
                .latitude(new BigDecimal("21.0288")).longitude(new BigDecimal("105.8524")).radiusM(40)
                .captureSource("FIELD").requiresCheckin(false).questions(List.of(q)).build();
        return QuestVersion.builder().id(versionId).questId(questId).rewardStars(rewardStars)
                .provinceCode("01").checkpoints(List.of(cp)).build();
    }

    @Test
    @DisplayName("a stable sample within the radius unlocks the checkpoint")
    void arrivalUnlocks() {
        when(runRepository.findRunCheckpoint(memberId, cpId)).thenReturn(Optional.empty());
        when(checkinVerifier.assess(any(VerificationTarget.class), any(), any(), any()))
                .thenReturn(new CheckinVerifier.Assessment(10.0, true, false, 40, true, Optional.empty()));

        QuestArrivalResponse response = service.submitSample(user, runId,
                new com.ds.goroute.quest.dto.QuestSampleRequest(cpId, new BigDecimal("21.0288"),
                        new BigDecimal("105.8524"), new BigDecimal("8"), null, false));

        assertThat(response.arrived()).isTrue();
        assertThat(response.stableStreak()).isEqualTo(1);
        verify(runRepository).updateRunCheckpointSample(any());
    }

    @Test
    @DisplayName("a sample outside the radius does not unlock and resets the streak")
    void missResetsStreak() {
        when(runRepository.findRunCheckpoint(memberId, cpId)).thenReturn(Optional.empty());
        when(checkinVerifier.assess(any(VerificationTarget.class), any(), any(), any()))
                .thenReturn(new CheckinVerifier.Assessment(800.0, false, false, 40, true, Optional.empty()));

        QuestArrivalResponse response = service.submitSample(user, runId,
                new com.ds.goroute.quest.dto.QuestSampleRequest(cpId, new BigDecimal("21.05"),
                        new BigDecimal("105.85"), new BigDecimal("8"), null, false));

        assertThat(response.arrived()).isFalse();
        assertThat(response.stableStreak()).isZero();
    }

    @Test
    @DisplayName("answering before arrival is forbidden")
    void answerBeforeArrivalForbidden() {
        when(runRepository.findRunCheckpoint(memberId, cpId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.answerQuestion(user, runId, qId, new QuestAnswerRequest("Hanoi", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Arrive");
    }

    @Test
    @DisplayName("a correct answer after arrival clears the checkpoint")
    void correctAnswerClears() {
        QuestRunCheckpoint unlocked = QuestRunCheckpoint.builder().id(UUID.randomUUID())
                .runId(runId).memberId(memberId).checkpointId(cpId)
                .stableStreak(1).unlockedAt(java.time.LocalDateTime.now()).dataVersion(1L).build();
        when(runRepository.findRunCheckpoint(memberId, cpId)).thenReturn(Optional.of(unlocked));
        when(runRepository.findRunQuestion(memberId, qId)).thenReturn(Optional.empty());
        // gate recompute reads all answers/checkpoints for the member
        when(runRepository.findRunQuestions(memberId)).thenReturn(List.of(
                QuestRunQuestion.builder().id(UUID.randomUUID()).memberId(memberId).questionId(qId)
                        .guessCount(1).correct(true).build()));
        when(runRepository.findRunCheckpoints(memberId)).thenReturn(List.of(unlocked));

        // "Ha Noi" (no diacritics) must match "Hà Nội" — whitespace is collapsed, not removed.
        var response = service.answerQuestion(user, runId, qId, new QuestAnswerRequest("Ha Noi", null));

        assertThat(response.correct()).isTrue();
        assertThat(response.checkpointCleared()).isTrue();
    }

    @Test
    @DisplayName("completing pays the reward once per (quest,user) and stamps the member, not the run")
    void completionRewardKeys() {
        QuestRunCheckpoint unlocked = QuestRunCheckpoint.builder().id(UUID.randomUUID())
                .runId(runId).memberId(memberId).checkpointId(cpId).unlockedAt(java.time.LocalDateTime.now()).build();
        when(runRepository.findRunQuestions(memberId)).thenReturn(List.of(
                QuestRunQuestion.builder().id(UUID.randomUUID()).memberId(memberId).questionId(qId).correct(true).build()));
        when(runRepository.findRunCheckpoints(memberId)).thenReturn(List.of(unlocked));
        when(runRepository.updateRunStatus(eq(runId), anyLong(), eq("COMPLETED"), any(), any())).thenReturn(true);
        // Not the creator → reward is paid.
        when(questRepository.findQuestById(questId)).thenReturn(Optional.of(
                Quest.builder().id(questId).creatorId(creatorId).build()));
        when(questRepository.findCreatorById(creatorId)).thenReturn(Optional.of(
                QuestCreatorProfile.builder().id(creatorId).userId(UUID.randomUUID()).build()));

        service.complete(user, runId);

        verify(starService).grant(eq(user), eq(50), eq("QUEST_COMPLETE"),
                eq("quest_complete:" + questId + ":" + user), any());
        ArgumentCaptor<PassportEvent> event = ArgumentCaptor.forClass(PassportEvent.class);
        verify(passportMapper).insertEvent(event.capture());
        assertThat(event.getValue().getSource()).isEqualTo("QUEST_RUN");
        assertThat(event.getValue().getSourceId()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("a creator playing their own quest earns no completion Stars")
    void creatorSelfPlayNoStars() {
        QuestRunCheckpoint unlocked = QuestRunCheckpoint.builder().id(UUID.randomUUID())
                .runId(runId).memberId(memberId).checkpointId(cpId).unlockedAt(java.time.LocalDateTime.now()).build();
        when(runRepository.findRunQuestions(memberId)).thenReturn(List.of(
                QuestRunQuestion.builder().id(UUID.randomUUID()).memberId(memberId).questionId(qId).correct(true).build()));
        when(runRepository.findRunCheckpoints(memberId)).thenReturn(List.of(unlocked));
        when(runRepository.updateRunStatus(eq(runId), anyLong(), eq("COMPLETED"), any(), any())).thenReturn(true);
        when(questRepository.findQuestById(questId)).thenReturn(Optional.of(
                Quest.builder().id(questId).creatorId(creatorId).build()));
        // The player IS the creator.
        when(questRepository.findCreatorById(creatorId)).thenReturn(Optional.of(
                QuestCreatorProfile.builder().id(creatorId).userId(user).build()));

        service.complete(user, runId);

        verify(starService, never()).grant(any(), org.mockito.ArgumentMatchers.anyInt(), any(), any(), any());
        verify(passportMapper).insertEvent(any()); // still gets the passport stamp
    }
}
