package com.ds.goroute.quest;

import com.ds.goroute.entity.StarTransaction;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.PassportMapper;
import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.domain.QuestCheckpoint;
import com.ds.goroute.quest.domain.QuestCheckpointClue;
import com.ds.goroute.quest.domain.QuestCheckpointStop;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.domain.QuestQuestion;
import com.ds.goroute.quest.domain.QuestRun;
import com.ds.goroute.quest.domain.QuestRunCheckpoint;
import com.ds.goroute.quest.domain.QuestRunClue;
import com.ds.goroute.quest.domain.QuestRunMember;
import com.ds.goroute.quest.domain.QuestRunStopVisit;
import com.ds.goroute.quest.domain.QuestVersion;
import com.ds.goroute.quest.dto.QuestProximityRequest;
import com.ds.goroute.quest.dto.QuestProximityResponse;
import com.ds.goroute.quest.dto.QuestRunResponse;
import com.ds.goroute.quest.dto.QuestSampleRequest;
import com.ds.goroute.quest.dto.QuestStopVisitRequest;
import com.ds.goroute.quest.dto.QuestStopVisitResponse;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.persistence.QuestRunRepository;
import com.ds.goroute.quest.service.QuestAnswerGrader;
import com.ds.goroute.quest.service.QuestEconomyService;
import com.ds.goroute.quest.service.QuestPlayService;
import com.ds.goroute.quest.service.QuestSearchArea;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.service.checkin.CheckinVerifier;
import com.ds.goroute.service.checkin.VerificationTarget;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.utils.GeoDistance;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("§3.14 dynamic checkpoints")
class QuestDynamicCheckpointTest {

    private static final BigDecimal SPOT_LAT = new BigDecimal("21.0288000");
    private static final BigDecimal SPOT_LNG = new BigDecimal("105.8524000");

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
    private final UUID questId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final UUID runId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();
    private final UUID cpId = UUID.randomUUID();
    private final UUID stopId = UUID.randomUUID();

    private final List<QuestRunCheckpoint> progress = new ArrayList<>();
    private final List<QuestRunClue> cluesBought = new ArrayList<>();
    private final List<QuestRunStopVisit> visits = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new QuestPlayService(questRepository, runRepository, checkinVerifier, new QuestAnswerGrader(),
                config, starService, passportMapper, new MarketplaceJson(new ObjectMapper()), economyService);
        when(config.getInt(BusinessConfigKey.QUEST_ARRIVAL_STABLE_SAMPLES)).thenReturn(1);
        when(config.getInt(BusinessConfigKey.QUEST_UNLOCK_RADIUS_METERS)).thenReturn(40);
        when(config.getInt(BusinessConfigKey.QUEST_ARRIVAL_CLIENT_INTERVAL_SECONDS)).thenReturn(5);
        when(config.getInt(BusinessConfigKey.QUEST_PROXIMITY_MIN_INTERVAL_SECONDS)).thenReturn(10);

        QuestRun run = QuestRun.builder().id(runId).questId(questId).questVersionId(versionId)
                .ownerUserId(user).status("IN_PROGRESS").dataVersion(1L).build();
        QuestRunMember member = QuestRunMember.builder().id(memberId).runId(runId).userId(user)
                .verification("UNVERIFIED").presenceCheckpoints(0).build();
        when(runRepository.findRunById(runId)).thenReturn(Optional.of(run));
        when(runRepository.findMember(runId, user)).thenReturn(Optional.of(member));
        when(runRepository.findRunCheckpoints(memberId)).thenReturn(progress);
        when(runRepository.findRunClues(memberId)).thenReturn(cluesBought);
        when(runRepository.findRunStopVisits(memberId)).thenReturn(visits);
        when(runRepository.findRunCheckpoint(eq(memberId), any())).thenAnswer(inv -> progress.stream()
                .filter(rc -> rc.getCheckpointId().equals(inv.getArgument(1))).findFirst());
        when(runRepository.insertRunClue(any())).thenReturn(true);

        when(questRepository.findQuestById(questId)).thenReturn(Optional.of(
                Quest.builder().id(questId).creatorId(creatorId).build()));
        when(questRepository.findCreatorById(creatorId)).thenReturn(Optional.of(
                QuestCreatorProfile.builder().id(creatorId).userId(creatorUser).build()));
    }

    private void play(QuestCheckpoint... checkpoints) {
        when(questRepository.loadVersionGraph(versionId)).thenReturn(Optional.of(QuestVersion.builder()
                .id(versionId).questId(questId).contentLanguage("vi")
                .checkpoints(List.of(checkpoints)).build()));
    }

    private QuestCheckpoint areaCheckpoint(QuestCheckpointClue... clues) {
        QuestSearchArea.Center center = QuestSearchArea.center(questId, SPOT_LAT, SPOT_LNG, 30, 200);
        return QuestCheckpoint.builder().id(cpId).sortOrder(0).name("Hidden stele")
                .latitude(SPOT_LAT).longitude(SPOT_LNG).radiusM(30)
                .findMode("AREA").searchRadiusM(200)
                .searchCenterLat(center.latitude()).searchCenterLng(center.longitude())
                .hotColdEnabled(true).completionMode("ARRIVE").story("Built in 1865.")
                .clues(List.of(clues)).questions(List.of()).stops(List.of()).build();
    }

    private static QuestCheckpointClue clue(int tier, String kind, int cost) {
        return QuestCheckpointClue.builder().id(UUID.randomUUID()).tier(tier).kind(kind)
                .text(kind.equals("TEXT") ? "Near the old well" : null).costStars(cost).build();
    }

    private void unlock(UUID checkpointId) {
        progress.add(QuestRunCheckpoint.builder().id(UUID.randomUUID()).runId(runId).memberId(memberId)
                .checkpointId(checkpointId).stableStreak(1).unlockedAt(LocalDateTime.now()).dataVersion(1L).build());
    }

    @Nested
    @DisplayName("finding (AREA)")
    class Finding {

        @Test
        @DisplayName("the search centre is seeded, offset from the spot, and the spot stays inside the circle")
        void searchCentre() {
            QuestSearchArea.Center first = QuestSearchArea.center(questId, SPOT_LAT, SPOT_LNG, 30, 200);
            QuestSearchArea.Center again = QuestSearchArea.center(questId, new BigDecimal("21.0288"),
                    new BigDecimal("105.8524"), 30, 200);
            assertThat(again).isEqualTo(first);

            for (int i = 0; i < 200; i++) {
                QuestSearchArea.Center c = QuestSearchArea.center(UUID.randomUUID(), SPOT_LAT, SPOT_LNG, 30, 200);
                double offset = GeoDistance.between(SPOT_LAT.doubleValue(), SPOT_LNG.doubleValue(),
                        c.latitude().doubleValue(), c.longitude().doubleValue());
                assertThat(offset).isLessThanOrEqualTo(0.6 * (200 - 30) + 1);
            }
        }

        @Test
        @DisplayName("before REVEAL the run hides the real spot and shows only the search area")
        void hidesSpotUntilReveal() {
            play(areaCheckpoint(clue(1, "TEXT", 0), clue(2, "REVEAL", 10)));

            QuestRunResponse.CurrentCheckpointView current = service.getRun(user, runId).current();

            assertThat(current.latitude()).isNull();
            assertThat(current.longitude()).isNull();
            assertThat(current.radiusM()).isNull();
            assertThat(current.searchArea()).isNotNull();
            assertThat(current.searchArea().radiusM()).isEqualTo(200);
            assertThat(current.story()).as("story waits for the unlock").isNull();
            assertThat(current.clues()).extracting(QuestRunResponse.ClueView::text).containsOnlyNulls();
            assertThat(current.assisted()).isFalse();
        }

        @Test
        @DisplayName("after REVEAL the spot is sent and the checkpoint is marked assisted")
        void revealShowsSpot() {
            play(areaCheckpoint(clue(1, "TEXT", 0), clue(2, "REVEAL", 10)));
            cluesBought.add(QuestRunClue.builder().checkpointId(cpId).tier(1).build());
            cluesBought.add(QuestRunClue.builder().checkpointId(cpId).tier(2).build());

            QuestRunResponse.CurrentCheckpointView current = service.getRun(user, runId).current();

            assertThat(current.latitude()).isEqualByComparingTo(SPOT_LAT);
            assertThat(current.assisted()).isTrue();
            assertThat(current.clues().get(0).text()).isEqualTo("Near the old well");
        }

        @Test
        @DisplayName("an AREA checkpoint unlocks inside the search circle, not the spot's circle")
        void unlockTargetIsSearchCircle() {
            QuestCheckpoint cp = areaCheckpoint();
            play(cp);
            when(checkinVerifier.assess(any(VerificationTarget.class), any(), any(), any()))
                    .thenReturn(new CheckinVerifier.Assessment(90.0, true, false, 200, true, Optional.empty()));

            service.submitSample(user, runId, new QuestSampleRequest(cpId, SPOT_LAT, SPOT_LNG,
                    new BigDecimal("8"), null, false));

            ArgumentCaptor<VerificationTarget> target = ArgumentCaptor.forClass(VerificationTarget.class);
            verify(checkinVerifier).assess(target.capture(), any(), any(), any());
            assertThat(target.getValue().effectiveRadiusMeters()).isEqualTo(200);
            assertThat(target.getValue().latitude()).isEqualByComparingTo(cp.getSearchCenterLat());
        }

        @Test
        @DisplayName("clues are bought in order, with a per-user Stars key, and the creator gets a share")
        void buyClue() {
            play(areaCheckpoint(clue(1, "TEXT", 5), clue(2, "REVEAL", 10)));
            UUID tx = UUID.randomUUID();
            when(starService.spend(eq(user), eq(5), eq("QUEST_CLUE"), anyString(), anyString()))
                    .thenReturn(StarTransaction.builder().id(tx).build());

            assertThatThrownBy(() -> service.buyClue(user, runId, cpId, 2))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("earlier clues");

            service.buyClue(user, runId, cpId, 1);

            String key = "quest_clue:" + runId + ":" + cpId + ":1:" + user;
            verify(starService).spend(eq(user), eq(5), eq("QUEST_CLUE"), eq(key), anyString());
            verify(economyService).creditClueSale(eq(questId), eq(runId), eq(user), eq(5), anyString());
        }

        @Test
        @DisplayName("a clue already owned is not charged again")
        void buyClueIdempotent() {
            play(areaCheckpoint(clue(1, "TEXT", 5)));
            cluesBought.add(QuestRunClue.builder().checkpointId(cpId).tier(1).build());

            service.buyClue(user, runId, cpId, 1);

            verify(starService, never()).spend(any(), anyInt(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("hot/cold answers a band and a trend, never a distance, and repeats itself inside the interval")
        void hotCold() {
            play(areaCheckpoint());
            QuestRunCheckpoint rc = QuestRunCheckpoint.builder().id(UUID.randomUUID()).runId(runId)
                    .memberId(memberId).checkpointId(cpId).stableStreak(0).dataVersion(1L)
                    .lastProximityM(new BigDecimal("300")).lastProximityAt(LocalDateTime.now().minusMinutes(1))
                    .proximityBand("COLD").build();
            progress.add(rc);

            QuestProximityResponse near = service.proximity(user, runId, cpId,
                    new QuestProximityRequest(SPOT_LAT, SPOT_LNG, new BigDecimal("5")));
            assertThat(near.band()).isEqualTo("HOT");
            assertThat(near.trend()).isEqualTo("CLOSER");

            // Asked again at once from far away: inside the interval, the previous answer comes back.
            QuestProximityResponse again = service.proximity(user, runId, cpId,
                    new QuestProximityRequest(new BigDecimal("21.05"), SPOT_LNG, new BigDecimal("5")));
            assertThat(again).isEqualTo(near);
        }

        @Test
        @DisplayName("band edges")
        void bands() {
            assertThat(QuestPlayService.proximityBand(25)).isEqualTo("HOT");
            assertThat(QuestPlayService.proximityBand(26)).isEqualTo("WARM");
            assertThat(QuestPlayService.proximityBand(60)).isEqualTo("WARM");
            assertThat(QuestPlayService.proximityBand(150)).isEqualTo("COOL");
            assertThat(QuestPlayService.proximityBand(151)).isEqualTo("COLD");
            assertThat(QuestPlayService.proximityTrend(null, 10)).isNull();
            assertThat(QuestPlayService.proximityTrend(100.0, 97)).isEqualTo("SAME");
            assertThat(QuestPlayService.proximityTrend(100.0, 90)).isEqualTo("CLOSER");
            assertThat(QuestPlayService.proximityTrend(100.0, 110)).isEqualTo("FARTHER");
        }
    }

    @Nested
    @DisplayName("completion modes")
    class Completion {

        @Test
        @DisplayName("B32: no checkpoint is cleared before it is unlocked, even with nothing left to do")
        void clearedNeedsUnlock() {
            QuestCheckpoint guide = QuestCheckpoint.builder().id(cpId).sortOrder(0).name("Gate")
                    .latitude(SPOT_LAT).longitude(SPOT_LNG).radiusM(40).completionMode("ARRIVE")
                    .story("A story").questions(List.of()).build();
            play(guide);

            assertThat(service.getRun(user, runId).clearedCheckpoints()).isZero();

            unlock(cpId);
            QuestRunResponse run = service.getRun(user, runId);
            assertThat(run.clearedCheckpoints()).isEqualTo(1);
            assertThat(run.completed()).isTrue();
        }

        @Test
        @DisplayName("STOPS clears only once min_stops points were heard by GPS")
        void stopsMode() {
            QuestCheckpointStop stop = QuestCheckpointStop.builder().id(stopId).sortOrder(0).name("Bridge")
                    .latitude(SPOT_LAT).longitude(SPOT_LNG).radiusM(30).imageUrls("[]").build();
            QuestCheckpoint cp = QuestCheckpoint.builder().id(cpId).sortOrder(0).name("Lake")
                    .latitude(SPOT_LAT).longitude(SPOT_LNG).radiusM(40).completionMode("STOPS").minStops(1)
                    .questions(List.of()).stops(List.of(stop)).build();
            play(cp);
            unlock(cpId);

            // A tap is recorded but does not count.
            visits.add(QuestRunStopVisit.builder().stopId(stopId).checkpointId(cpId).via("TAP")
                    .countsTowardCompletion(false).build());
            QuestRunResponse tapped = service.getRun(user, runId);
            assertThat(tapped.clearedCheckpoints()).isZero();
            assertThat(tapped.current().stops()).singleElement()
                    .satisfies(view -> assertThat(view.visited()).isTrue());

            visits.clear();
            visits.add(QuestRunStopVisit.builder().stopId(stopId).checkpointId(cpId).via("GPS")
                    .countsTowardCompletion(true).build());
            assertThat(service.getRun(user, runId).clearedCheckpoints()).isEqualTo(1);
        }

        @Test
        @DisplayName("a GPS visit inside the point counts; a tap never does; a locked checkpoint refuses")
        void visitStop() {
            QuestCheckpointStop stop = QuestCheckpointStop.builder().id(stopId).sortOrder(0).name("Bridge")
                    .latitude(SPOT_LAT).longitude(SPOT_LNG).radiusM(30).imageUrls("[]").build();
            QuestCheckpoint cp = QuestCheckpoint.builder().id(cpId).sortOrder(0).name("Lake")
                    .latitude(SPOT_LAT).longitude(SPOT_LNG).radiusM(40).completionMode("STOPS").minStops(1)
                    .questions(List.of()).stops(List.of(stop)).build();
            play(cp);

            assertThatThrownBy(() -> service.visitStop(user, runId, stopId, new QuestStopVisitRequest(null, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Arrive");

            unlock(cpId);
            when(checkinVerifier.assess(any(VerificationTarget.class), any(), any(), any()))
                    .thenReturn(new CheckinVerifier.Assessment(4.0, true, false, 30, true, Optional.empty()));
            when(runRepository.findRunStopVisits(memberId)).thenReturn(List.of(QuestRunStopVisit.builder()
                    .stopId(stopId).checkpointId(cpId).via("GPS").countsTowardCompletion(true).build()));

            QuestStopVisitResponse response = service.visitStop(user, runId, stopId,
                    new QuestStopVisitRequest(SPOT_LAT, SPOT_LNG, new BigDecimal("5")));

            ArgumentCaptor<QuestRunStopVisit> saved = ArgumentCaptor.forClass(QuestRunStopVisit.class);
            verify(runRepository).upsertRunStopVisit(saved.capture());
            assertThat(saved.getValue().getVia()).isEqualTo("GPS");
            assertThat(saved.getValue().isCountsTowardCompletion()).isTrue();
            assertThat(response.stopsCounted()).isEqualTo(1);
        }

        @Test
        @DisplayName("a TASK checkpoint still needs its required question after unlocking")
        void taskStillNeedsAnswer() {
            QuestQuestion q = QuestQuestion.builder().id(UUID.randomUUID()).sortOrder(0).required(true)
                    .bonus(false).type("TEXT").prompt("?").answerPlain("x").choices(List.of()).build();
            QuestCheckpoint cp = QuestCheckpoint.builder().id(cpId).sortOrder(0).name("Gate")
                    .latitude(SPOT_LAT).longitude(SPOT_LNG).radiusM(40).questions(List.of(q)).build();
            play(cp);
            unlock(cpId);

            assertThat(service.getRun(user, runId).clearedCheckpoints()).isZero();
        }
    }

    @Nested
    @DisplayName("route preview")
    class RoutePreview {

        @Test
        @DisplayName("the current checkpoint tells what waits there before arrival, without the content")
        void currentPreview() {
            QuestQuestion q = QuestQuestion.builder().id(UUID.randomUUID()).sortOrder(0).required(true)
                    .bonus(false).type("TEXT").prompt("What year?").answerPlain("1865").choices(List.of()).build();
            QuestCheckpointStop stop = QuestCheckpointStop.builder().id(stopId).sortOrder(0).name("Bridge")
                    .latitude(SPOT_LAT).longitude(SPOT_LNG).radiusM(30).story("Red bridge").build();
            QuestCheckpoint cp = QuestCheckpoint.builder().id(cpId).sortOrder(0).name("Gate")
                    .latitude(SPOT_LAT).longitude(SPOT_LNG).radiusM(40).requiresCheckin(true)
                    .story("Built in 1865.").storyAudioUrl("https://cdn/gate.m4a").storyAudioSeconds(42)
                    .questions(List.of(q)).stops(List.of(stop)).build();
            play(cp);

            QuestRunResponse.CurrentCheckpointView current = service.getRun(user, runId).current();

            assertThat(current.questions()).as("PIN questions open on arrival").isEmpty();
            assertThat(current.story()).isNull();
            assertThat(current.stops()).isEmpty();
            QuestRunResponse.CheckpointPreview preview = current.preview();
            assertThat(preview.questionCount()).isEqualTo(1);
            assertThat(preview.requiredQuestionCount()).isEqualTo(1);
            assertThat(preview.requiresCheckin()).isTrue();
            assertThat(preview.stopCount()).isEqualTo(1);
            assertThat(preview.hasStory()).isTrue();
            assertThat(preview.hasStoryAudio()).isTrue();
            assertThat(preview.storyAudioSeconds()).isEqualTo(42);
        }

        @Test
        @DisplayName("checkpoints ahead are listed in order with public facts only")
        void upcoming() {
            UUID secondId = UUID.randomUUID();
            UUID thirdId = UUID.randomUUID();
            QuestCheckpoint first = QuestCheckpoint.builder().id(cpId).sortOrder(0).name("Gate")
                    .latitude(SPOT_LAT).longitude(SPOT_LNG).radiusM(40).build();
            QuestCheckpoint second = QuestCheckpoint.builder().id(secondId).sortOrder(1).name("Temple")
                    .category("TEMPLE").latitude(SPOT_LAT).longitude(SPOT_LNG).radiusM(40)
                    .completionMode("STOPS").minStops(2).build();
            QuestCheckpoint third = QuestCheckpoint.builder().id(thirdId).sortOrder(2).name("Hidden stele")
                    .latitude(SPOT_LAT).longitude(SPOT_LNG).radiusM(30).findMode("AREA").searchRadiusM(200)
                    .story("Built in 1865.").build();
            play(first, second, third);

            QuestRunResponse run = service.getRun(user, runId);

            assertThat(run.current().checkpointId()).isEqualTo(cpId);
            assertThat(run.upcoming()).extracting(QuestRunResponse.UpcomingCheckpointView::checkpointId)
                    .containsExactly(secondId, thirdId);
            QuestRunResponse.UpcomingCheckpointView temple = run.upcoming().get(0);
            assertThat(temple.name()).isEqualTo("Temple");
            assertThat(temple.category()).isEqualTo("TEMPLE");
            assertThat(temple.completionMode()).isEqualTo("STOPS");
            assertThat(temple.minStops()).isEqualTo(2);
            assertThat(run.upcoming().get(1).findMode()).isEqualTo("AREA");
            assertThat(run.upcoming().get(1).preview().hasStory()).isTrue();

            unlock(cpId);
            assertThat(service.getRun(user, runId).upcoming())
                    .as("a cleared checkpoint leaves the list; the next one becomes current")
                    .extracting(QuestRunResponse.UpcomingCheckpointView::checkpointId)
                    .containsExactly(thirdId);
        }
    }
}
