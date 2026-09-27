package com.ds.goroute.quest;

import com.ds.goroute.entity.StarTransaction;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.PassportMapper;
import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.domain.QuestCheckpoint;
import com.ds.goroute.quest.domain.QuestCheckpointClue;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.domain.QuestQuestion;
import com.ds.goroute.quest.domain.QuestQuestionChoice;
import com.ds.goroute.quest.domain.QuestRun;
import com.ds.goroute.quest.domain.QuestRunCheckpoint;
import com.ds.goroute.quest.domain.QuestRunClue;
import com.ds.goroute.quest.domain.QuestRunMember;
import com.ds.goroute.quest.domain.QuestRunQuestion;
import com.ds.goroute.quest.domain.QuestVersion;
import com.ds.goroute.quest.dto.QuestLocalRunRequest;
import com.ds.goroute.quest.dto.QuestLocalRunResponse;
import com.ds.goroute.quest.dto.QuestPackResponse;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.persistence.QuestRunRepository;
import com.ds.goroute.quest.service.QuestAnswerGrader;
import com.ds.goroute.quest.service.QuestEconomyService;
import com.ds.goroute.quest.service.QuestPlayService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.service.checkin.CheckinVerifier;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.ds.goroute.type.BusinessConfigKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Local-first play: the pack hands an entitled player the whole quest, and an uploaded run is
 * replayed — re-graded and re-checked — before anything is granted. The run repository is an
 * in-memory stand-in so the replay reads back what it wrote.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("local-first quest play")
class QuestLocalPlayTest {

    private static final BigDecimal LAT = new BigDecimal("21.0288000");
    private static final BigDecimal LNG = new BigDecimal("105.8524000");

    @Mock private QuestRepository questRepository;
    @Mock private QuestRunRepository runRepository;
    @Mock private CheckinVerifier checkinVerifier;
    @Mock private BusinessConfigService config;
    @Mock private StarService starService;
    @Mock private PassportMapper passportMapper;
    @Mock private QuestEconomyService economyService;

    private QuestPlayService service;

    private final UUID user = UUID.randomUUID();
    private final UUID creatorUser = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();
    private final UUID questId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final UUID cpId = UUID.randomUUID();
    private final UUID textQ = UUID.randomUUID();
    private final UUID choiceQ = UUID.randomUUID();
    private final UUID right = UUID.randomUUID();
    private final UUID wrong = UUID.randomUUID();

    private int priceStars;
    private final List<QuestRun> runs = new ArrayList<>();
    private final List<QuestRunMember> members = new ArrayList<>();
    private final List<QuestRunCheckpoint> checkpoints = new ArrayList<>();
    private final List<QuestRunQuestion> questions = new ArrayList<>();
    private final List<QuestRunClue> clues = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new QuestPlayService(questRepository, runRepository, checkinVerifier, new QuestAnswerGrader(),
                config, starService, passportMapper, new MarketplaceJson(new ObjectMapper()), economyService);
        when(config.getInt(BusinessConfigKey.QUEST_ARRIVAL_STABLE_SAMPLES)).thenReturn(2);
        when(config.getInt(BusinessConfigKey.QUEST_ARRIVAL_CLIENT_INTERVAL_SECONDS)).thenReturn(5);
        when(config.getInt(BusinessConfigKey.QUEST_UNLOCK_RADIUS_METERS)).thenReturn(40);
        when(config.getInt(BusinessConfigKey.CHECKIN_MAX_ACCURACY_METERS)).thenReturn(50);
        when(config.getInt(BusinessConfigKey.QUEST_MAX_GUESS_ATTEMPTS)).thenReturn(5);
        when(config.getInt(BusinessConfigKey.QUEST_PROXIMITY_MIN_INTERVAL_SECONDS)).thenReturn(10);
        when(config.getInt(BusinessConfigKey.QUEST_GROUP_PRESENCE_PCT)).thenReturn(70);

        when(questRepository.findQuestById(questId)).thenAnswer(inv -> Optional.of(Quest.builder()
                .id(questId).creatorId(creatorId).status("PUBLISHED").publishedVersionId(versionId).build()));
        when(questRepository.findCreatorById(creatorId)).thenReturn(Optional.of(
                QuestCreatorProfile.builder().id(creatorId).userId(creatorUser).build()));
        when(questRepository.loadVersionGraph(versionId)).thenAnswer(inv -> Optional.of(version()));

        when(runRepository.findRunByClientId(any(), anyString())).thenAnswer(inv -> runs.stream()
                .filter(r -> inv.getArgument(1).equals(r.getClientRunId())).findFirst());
        org.mockito.Mockito.doAnswer(inv -> runs.add(inv.getArgument(0))).when(runRepository).insertRun(any());
        when(runRepository.findRunById(any())).thenAnswer(inv -> runs.stream()
                .filter(r -> r.getId().equals(inv.getArgument(0))).findFirst());
        when(runRepository.updateRunStatus(any(), anyLong(), anyString(), any(), any())).thenAnswer(inv -> {
            runs.stream().filter(r -> r.getId().equals(inv.getArgument(0))).forEach(r -> r.setStatus(inv.getArgument(2)));
            return true;
        });
        org.mockito.Mockito.doAnswer(inv -> members.add(inv.getArgument(0))).when(runRepository).insertMember(any());
        when(runRepository.findMember(any(), any())).thenAnswer(inv -> members.stream()
                .filter(m -> m.getRunId().equals(inv.getArgument(0)) && m.getUserId().equals(inv.getArgument(1)))
                .findFirst());
        when(runRepository.findMembers(any())).thenAnswer(inv -> members.stream()
                .filter(m -> m.getRunId().equals(inv.getArgument(0))).toList());
        org.mockito.Mockito.doAnswer(inv -> {
            members.stream().filter(m -> m.getId().equals(inv.getArgument(0))).forEach(m -> m.setRewarded(true));
            return null;
        }).when(runRepository).markMemberRewarded(any());

        org.mockito.Mockito.doAnswer(inv -> checkpoints.add(inv.getArgument(0)))
                .when(runRepository).insertRunCheckpoint(any());
        when(runRepository.findRunCheckpoints(any())).thenAnswer(inv -> checkpoints.stream()
                .filter(c -> c.getMemberId().equals(inv.getArgument(0))).toList());
        when(runRepository.findRunCheckpoint(any(), any())).thenAnswer(inv -> checkpoints.stream()
                .filter(c -> c.getMemberId().equals(inv.getArgument(0))
                        && c.getCheckpointId().equals(inv.getArgument(1))).findFirst());
        org.mockito.Mockito.doAnswer(inv -> {
            checkpoints.stream().filter(c -> c.getId().equals(inv.getArgument(0)))
                    .forEach(c -> c.setCheckinState(inv.getArgument(2)));
            return null;
        }).when(runRepository).updateRunCheckpointCheckin(any(), any(), any());

        org.mockito.Mockito.doAnswer(inv -> questions.add(inv.getArgument(0)))
                .when(runRepository).insertRunQuestion(any());
        when(runRepository.findRunQuestions(any())).thenAnswer(inv -> questions.stream()
                .filter(q -> q.getMemberId().equals(inv.getArgument(0))).toList());
        when(runRepository.findRunQuestion(any(), any())).thenAnswer(inv -> questions.stream()
                .filter(q -> q.getMemberId().equals(inv.getArgument(0))
                        && q.getQuestionId().equals(inv.getArgument(1))).findFirst());
        when(runRepository.insertRunClue(any())).thenAnswer(inv -> clues.add(inv.getArgument(0)));
        when(runRepository.findRunClues(any())).thenAnswer(inv -> clues.stream()
                .filter(c -> c.getMemberId().equals(inv.getArgument(0))).toList());
        when(runRepository.findRunStopVisits(any())).thenReturn(List.of());
        when(starService.spend(any(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(StarTransaction.builder().id(UUID.randomUUID()).build());
    }

    private QuestVersion version() {
        QuestQuestion text = QuestQuestion.builder().id(textQ).sortOrder(0).required(true).bonus(false)
                .type("TEXT").prompt("City?").answerPlain("Hà Nội").answerVariants("[\"Hanoi\"]")
                .hintTier1("Capital").choices(List.of()).build();
        QuestQuestion choice = QuestQuestion.builder().id(choiceQ).sortOrder(1).required(true).bonus(false)
                .type("CHOICE").prompt("Colour?").choices(List.of(
                        QuestQuestionChoice.builder().id(right).sortOrder(0).content("Red").correct(true).build(),
                        QuestQuestionChoice.builder().id(wrong).sortOrder(1).content("Blue").correct(false).build()))
                .build();
        QuestCheckpoint cp = QuestCheckpoint.builder().id(cpId).sortOrder(0).name("Gate")
                .latitude(LAT).longitude(LNG).radiusM(40).requiresCheckin(true)
                .story("Built in 1865.").questions(List.of(text, choice))
                .clues(List.of(QuestCheckpointClue.builder().tier(1).kind("TEXT").text("Near the well")
                        .costStars(5).build()))
                .build();
        return QuestVersion.builder().id(versionId).questId(questId).version(3).contentRevision(1)
                .title("Old Quarter").contentLanguage("vi").priceStars(priceStars).rewardStars(20)
                .provinceCode("01").checkpoints(List.of(cp)).build();
    }

    private QuestLocalRunRequest upload(String clientId, BigDecimal lat, String answer, List<QuestLocalRunRequest.Clue> used) {
        return new QuestLocalRunRequest(clientId, versionId, LocalDateTime.now().minusHours(1), LocalDateTime.now(),
                List.of(new QuestLocalRunRequest.Arrival(cpId, lat, LNG, new BigDecimal("8"), LocalDateTime.now())),
                List.of(new QuestLocalRunRequest.Answer(textQ, answer, null, 2),
                        new QuestLocalRunRequest.Answer(choiceQ, null, List.of(right), 1)),
                used, List.of(), List.of());
    }

    @Test
    @DisplayName("the pack carries everything the phone needs to play and grade")
    void pack() {
        QuestPackResponse pack = service.pack(user, questId);

        assertThat(pack.versionId()).isEqualTo(versionId);
        assertThat(pack.language()).isEqualTo("vi");
        assertThat(pack.settings().arrivalStableSamples()).isEqualTo(2);
        assertThat(pack.settings().maxGuesses()).isEqualTo(5);
        QuestPackResponse.Checkpoint cp = pack.checkpoints().get(0);
        assertThat(cp.latitude()).isEqualByComparingTo(LAT);
        assertThat(cp.story()).isEqualTo("Built in 1865.");
        assertThat(cp.clues().get(0).text()).isEqualTo("Near the well");
        assertThat(cp.questions().get(0).answerPlain()).isEqualTo("Hà Nội");
        assertThat(cp.questions().get(0).answerVariants()).containsExactly("Hanoi");
        assertThat(cp.questions().get(1).choices()).filteredOn(QuestPackResponse.Choice::correct)
                .extracting(QuestPackResponse.Choice::id).containsExactly(right);
    }

    @Test
    @DisplayName("a paid quest is not handed out before it is unlocked")
    void packNeedsEntitlement() {
        priceStars = 30;
        when(runRepository.findEntitlement(questId, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pack(user, questId)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("a run that holds up on replay is completed and rewarded, once")
    void syncRewards() {
        QuestLocalRunResponse first = service.syncLocalRun(user, questId,
                upload("local-1", LAT, "hanoi", List.of(new QuestLocalRunRequest.Clue(cpId, 1, null))));

        assertThat(first.completed()).isTrue();
        assertThat(first.rewarded()).isTrue();
        assertThat(first.clearedCheckpoints()).isEqualTo(1);
        verify(starService).grant(eq(user), eq(20), eq("QUEST_COMPLETE"), anyString(), anyString());
        verify(starService).spend(eq(user), eq(5), eq("QUEST_CLUE"), anyString(), anyString());
        assertThat(checkpoints.get(0).getCheckinState()).as("arriving stands in for the check-in").isEqualTo("WAIVED");

        QuestLocalRunResponse again = service.syncLocalRun(user, questId,
                upload("local-1", LAT, "hanoi", List.of()));
        assertThat(again.runId()).isEqualTo(first.runId());
        assertThat(runs).hasSize(1);
    }

    @Test
    @DisplayName("an arrival far from the checkpoint or a wrong answer is not believed")
    void syncReplays() {
        BigDecimal kilometreNorth = LAT.add(new BigDecimal("0.009"));
        QuestLocalRunResponse far = service.syncLocalRun(user, questId,
                upload("local-far", kilometreNorth, "hanoi", List.of()));
        assertThat(far.completed()).isFalse();
        assertThat(far.status()).isEqualTo("ABANDONED");

        QuestLocalRunResponse wrongAnswer = service.syncLocalRun(user, questId,
                upload("local-wrong", LAT, "Sài Gòn", List.of()));
        assertThat(wrongAnswer.completed()).isFalse();
        verify(starService, never()).grant(any(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("a clue that cannot be paid for keeps the run but withholds the reward")
    void unpaidClue() {
        when(starService.spend(any(), anyInt(), anyString(), anyString(), anyString()))
                .thenThrow(new BusinessException(com.ds.goroute.constant.ErrorConstant.INVALID_PARAMETERS, "Not enough"));

        QuestLocalRunResponse response = service.syncLocalRun(user, questId,
                upload("local-poor", LAT, "Hà Nội", List.of(new QuestLocalRunRequest.Clue(cpId, 1, null))));

        assertThat(response.completed()).isTrue();
        assertThat(response.unpaidClues()).isEqualTo(1);
        assertThat(response.rewarded()).isFalse();
    }
}
