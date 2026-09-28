package com.ds.goroute.quest;

import com.ds.goroute.mapper.PassportMapper;
import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.domain.QuestArObject;
import com.ds.goroute.quest.domain.QuestArObjectAsset;
import com.ds.goroute.quest.domain.QuestCheckpoint;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.domain.QuestRun;
import com.ds.goroute.quest.domain.QuestRunCheckpoint;
import com.ds.goroute.quest.domain.QuestRunMember;
import com.ds.goroute.quest.domain.QuestVersion;
import com.ds.goroute.quest.dto.QuestArObjectView;
import com.ds.goroute.quest.dto.QuestArTapRequest;
import com.ds.goroute.quest.dto.QuestArTapResponse;
import com.ds.goroute.quest.dto.QuestLocalRunRequest;
import com.ds.goroute.quest.dto.QuestLocalRunResponse;
import com.ds.goroute.quest.dto.QuestPackResponse;
import com.ds.goroute.quest.dto.QuestRunResponse;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * AR objects (§3.15) at play: a tap clears the checkpoint only within reach of the object, the
 * replay of a local run re-checks it, and an app that cannot show AR plays the checkpoint as ARRIVE
 * so it is never stuck. The run repository is an in-memory stand-in.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AR objects at play")
class QuestArObjectPlayTest {

    /** The checkpoint; the object stands ~110 m north of it. */
    private static final BigDecimal CP_LAT = new BigDecimal("21.0288000");
    private static final BigDecimal CP_LNG = new BigDecimal("105.8524000");
    private static final BigDecimal OBJ_LAT = new BigDecimal("21.0298000");
    private static final BigDecimal METRE = new BigDecimal("0.0000090");

    @Mock private QuestRepository questRepository;
    @Mock private QuestRunRepository runRepository;
    @Mock private CheckinVerifier checkinVerifier;
    @Mock private BusinessConfigService config;
    @Mock private StarService starService;
    @Mock private PassportMapper passportMapper;
    @Mock private QuestEconomyService economyService;

    private final MarketplaceJson json = new MarketplaceJson(new ObjectMapper());
    private QuestPlayService service;

    private final UUID user = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();
    private final UUID questId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final UUID cpId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();

    private String behavior = "FIXED";
    private final List<QuestRun> runs = new ArrayList<>();
    private final List<QuestRunMember> members = new ArrayList<>();
    private final List<QuestRunCheckpoint> checkpoints = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new QuestPlayService(questRepository, runRepository, checkinVerifier, new QuestAnswerGrader(),
                config, starService, passportMapper, json, economyService);
        when(config.getInt(BusinessConfigKey.QUEST_ARRIVAL_STABLE_SAMPLES)).thenReturn(2);
        when(config.getInt(BusinessConfigKey.QUEST_UNLOCK_RADIUS_METERS)).thenReturn(40);
        when(config.getInt(BusinessConfigKey.CHECKIN_MAX_ACCURACY_METERS)).thenReturn(50);
        when(config.getInt(BusinessConfigKey.QUEST_AR_INTERACT_RADIUS_METERS)).thenReturn(40);
        when(config.getInt(BusinessConfigKey.QUEST_GROUP_PRESENCE_PCT)).thenReturn(70);

        when(questRepository.findQuestById(questId)).thenAnswer(inv -> Optional.of(Quest.builder()
                .id(questId).creatorId(creatorId).status("PUBLISHED").publishedVersionId(versionId).build()));
        when(questRepository.findCreatorById(creatorId)).thenReturn(Optional.of(
                QuestCreatorProfile.builder().id(creatorId).userId(UUID.randomUUID()).build()));
        when(questRepository.loadVersionGraph(versionId)).thenAnswer(inv -> Optional.of(version()));
        when(questRepository.findArObjectAssets(any(java.util.Collection.class))).thenAnswer(inv -> Map.of(assetId,
                QuestArObjectAsset.builder().id(assetId).name("Scroll").glbUrl("https://cdn/scroll.glb")
                        .usdzUrl("https://cdn/scroll.usdz").heightM(new BigDecimal("0.4"))
                        .clips("[{\"name\":\"idle\",\"seconds\":2.0}]").canWander(true).active(true).build()));

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
            members.stream().filter(m -> m.getId().equals(inv.getArgument(0))).forEach(m -> m.setArSupported(true));
            return null;
        }).when(runRepository).markMemberArSupported(any());
        org.mockito.Mockito.doAnswer(inv -> checkpoints.add(inv.getArgument(0)))
                .when(runRepository).insertRunCheckpoint(any());
        when(runRepository.findRunCheckpoints(any())).thenAnswer(inv -> checkpoints.stream()
                .filter(c -> c.getMemberId().equals(inv.getArgument(0))).toList());
        when(runRepository.findRunCheckpoint(any(), any())).thenAnswer(inv -> checkpoints.stream()
                .filter(c -> c.getMemberId().equals(inv.getArgument(0))
                        && c.getCheckpointId().equals(inv.getArgument(1))).findFirst());
        // The object lives on the row the service mutated; nothing else to copy.
        when(runRepository.updateRunCheckpointArTap(any())).thenReturn(true);
        when(runRepository.findRunQuestions(any())).thenReturn(List.of());
        when(runRepository.findRunClues(any())).thenReturn(List.of());
        when(runRepository.findRunStopVisits(any())).thenReturn(List.of());
    }

    private QuestVersion version() {
        QuestArObject object = new QuestArObject(assetId, behavior, "IMAGE", OBJ_LAT, CP_LNG, 90, 150,
                "WANDER".equals(behavior) ? 30 : null, BigDecimal.ONE,
                List.of(new QuestArObject.Marker("https://cdn/stele.jpg", new BigDecimal("0.6"),
                        new QuestArObject.Vec3(new BigDecimal("0.5"), BigDecimal.ZERO, new BigDecimal("1.2")),
                        new BigDecimal("15"))),
                "Imperial scroll", "Sealed in 1802.", List.of("https://cdn/scroll.jpg"),
                "https://cdn/scroll.m4a", 42, null);
        QuestCheckpoint cp = QuestCheckpoint.builder().id(cpId).sortOrder(0).name("Temple")
                .latitude(CP_LAT).longitude(CP_LNG).radiusM(40).completionMode("AR_OBJECT")
                .arObject(json.write(object)).questions(List.of()).build();
        return QuestVersion.builder().id(versionId).questId(questId).version(1).contentRevision(0)
                .title("Temple walk").contentLanguage("vi").priceStars(0).rewardStars(10)
                .checkpoints(List.of(cp)).build();
    }

    private QuestLocalRunRequest upload(String clientId, List<QuestLocalRunRequest.ArTap> taps) {
        return new QuestLocalRunRequest(clientId, versionId, LocalDateTime.now().minusHours(1), LocalDateTime.now(),
                List.of(new QuestLocalRunRequest.Arrival(cpId, CP_LAT, CP_LNG, new BigDecimal("8"), LocalDateTime.now())),
                List.of(), List.of(), List.of(), taps);
    }

    private QuestLocalRunRequest.ArTap tap(BigDecimal lat) {
        return new QuestLocalRunRequest.ArTap(cpId, lat, CP_LNG, new BigDecimal("6"), "IMAGE", LocalDateTime.now());
    }

    @Test
    @DisplayName("an app that shows AR gets the object with its model files and everything it reveals")
    void packWithAr() {
        QuestPackResponse pack = service.pack(user, questId, true);

        QuestPackResponse.Checkpoint cp = pack.checkpoints().get(0);
        assertThat(cp.completionMode()).isEqualTo("AR_OBJECT");
        QuestArObjectView object = cp.arObject();
        assertThat(object.glbUrl()).isEqualTo("https://cdn/scroll.glb");
        assertThat(object.usdzUrl()).isEqualTo("https://cdn/scroll.usdz");
        assertThat(object.clips()).extracting(QuestArObjectView.Clip::name).containsExactly("idle");
        assertThat(object.interactRadiusM()).isEqualTo(40);
        assertThat(object.markers().get(0).offsetZ()).isEqualByComparingTo("1.2");
        assertThat(object.description()).isEqualTo("Sealed in 1802.");
        assertThat(pack.settings().arInteractRadiusM()).isEqualTo(40);
    }

    @Test
    @DisplayName("an older app gets the checkpoint as ARRIVE and no object")
    void packWithoutAr() {
        QuestPackResponse.Checkpoint cp = service.pack(user, questId, false).checkpoints().get(0);

        assertThat(cp.completionMode()).isEqualTo("ARRIVE");
        assertThat(cp.arObject()).isNull();
    }

    @Test
    @DisplayName("replay: a tap within reach of the object clears the checkpoint and completes the run")
    void syncTapWithinReach() {
        QuestLocalRunResponse response = service.syncLocalRun(user, questId,
                upload("ar-1", List.of(tap(OBJ_LAT.add(METRE.multiply(BigDecimal.valueOf(20)))))), true);

        assertThat(response.completed()).isTrue();
        assertThat(checkpoints.get(0).getArTappedAt()).isNotNull();
        assertThat(checkpoints.get(0).getArAnchorMode()).isEqualTo("IMAGE");
    }

    @Test
    @DisplayName("replay: a tap far from the object, or none at all, does not clear it")
    void syncTapTooFar() {
        QuestLocalRunResponse far = service.syncLocalRun(user, questId,
                upload("ar-far", List.of(tap(CP_LAT))), true);
        assertThat(far.completed()).as("the tap was at the checkpoint, 110 m from the object").isFalse();

        QuestLocalRunResponse none = service.syncLocalRun(user, questId, upload("ar-none", List.of()), true);
        assertThat(none.completed()).isFalse();
    }

    @Test
    @DisplayName("replay: a wandering object is reachable anywhere in its zone")
    void wanderZone() {
        behavior = "WANDER";
        BigDecimal sixtyMetres = OBJ_LAT.add(METRE.multiply(BigDecimal.valueOf(60)));

        assertThat(service.syncLocalRun(user, questId, upload("ar-wander", List.of(tap(sixtyMetres))), true)
                .completed()).as("40 m reach + 30 m zone").isTrue();
    }

    @Test
    @DisplayName("replay: an app that could not show AR clears the checkpoint on arrival, as ARRIVE")
    void syncWithoutAr() {
        QuestLocalRunResponse response = service.syncLocalRun(user, questId, upload("old-app", List.of()), false);

        assertThat(response.completed()).isTrue();
    }

    @Test
    @DisplayName("online: the object shows before the tap without what it reveals; a tap in reach clears it")
    void onlineTap() {
        QuestRunResponse started = service.startRun(user, questId, null, true);
        QuestArObjectView before = started.current().arObject();
        assertThat(before.title()).isEqualTo("Imperial scroll");
        assertThat(before.description()).as("revealed on tapping").isNull();
        assertThat(before.audioUrl()).isNull();

        QuestArTapResponse far = service.tapArObject(user, started.runId(), cpId,
                new QuestArTapRequest(CP_LAT, CP_LNG, new BigDecimal("5"), "APPROX"));
        assertThat(far.accepted()).isFalse();

        QuestArTapResponse near = service.tapArObject(user, started.runId(), cpId,
                new QuestArTapRequest(OBJ_LAT, CP_LNG, new BigDecimal("5"), "APPROX"));
        assertThat(near.accepted()).isTrue();
        assertThat(near.cleared()).isTrue();
        assertThat(near.run().completed()).isTrue();
        assertThat(near.object().description())
                .as("the run has moved on, so the tap itself carries what the object reveals")
                .isEqualTo("Sealed in 1802.");
        assertThat(near.object().audioUrl()).isEqualTo("https://cdn/scroll.m4a");
        assertThat(far.object()).isNull();
        assertThat(checkpoints.get(0).isUnlocked()).as("the tap is proof of presence").isTrue();
    }

    @Test
    @DisplayName("online: an older app sees ARRIVE; the same member upgrading sees the object")
    void onlineUpgrade() {
        QuestRunResponse old = service.startRun(user, questId, null, false);
        assertThat(old.current().completionMode()).isEqualTo("ARRIVE");
        assertThat(old.current().arObject()).isNull();

        QuestRunResponse upgraded = service.getRun(user, old.runId(), true);
        assertThat(upgraded.current().completionMode()).isEqualTo("AR_OBJECT");
        assertThat(upgraded.current().arObject()).isNotNull();
    }
}
