package com.ds.goroute.quest;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.domain.QuestArObject;
import com.ds.goroute.quest.domain.QuestArObjectAsset;
import com.ds.goroute.quest.domain.QuestCheckpoint;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.domain.QuestVersion;
import com.ds.goroute.quest.dto.SaveQuestDraftRequest;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.service.QuestBuilderServiceImpl;
import com.ds.goroute.quest.service.QuestCreatorGate;
import com.ds.goroute.quest.service.QuestDraftValidator;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.ds.goroute.type.QuestStatus;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Saving AR objects (§3.15): only a beta tester (or anyone once AR_ENABLED is thrown) may add or
 * change one, but an object sent back unchanged — the console's read-only round trip — always saves.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("saving AR objects")
class QuestArObjectBuilderTest {

    @Mock private QuestRepository repository;
    @Mock private QuestDraftValidator validator;
    @Mock private QuestCreatorGate creatorGate;

    private final MarketplaceJson json = new MarketplaceJson(new ObjectMapper());
    private QuestBuilderServiceImpl service;

    private final UUID owner = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();
    private final UUID questId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();
    private final List<QuestCheckpoint> stored = new ArrayList<>();
    private boolean assetCanWander = true;

    @BeforeEach
    void setUp() {
        service = new QuestBuilderServiceImpl(repository, validator, creatorGate, json);
        when(repository.findQuestById(questId)).thenReturn(Optional.of(Quest.builder().id(questId)
                .creatorId(creatorId).origin("COMMUNITY").status(QuestStatus.DRAFT.name())
                .draftVersionId(versionId).dataVersion(1L).build()));
        when(repository.findCreatorById(creatorId)).thenReturn(Optional.of(
                QuestCreatorProfile.builder().id(creatorId).userId(owner).status("ACTIVE").build()));
        when(repository.updateStatus(eq(questId), anyLong(), eq("DRAFT"), any(), any())).thenReturn(true);
        when(repository.findVersionById(versionId)).thenReturn(Optional.of(QuestVersion.builder().id(versionId).build()));
        when(repository.loadVersionGraph(versionId)).thenAnswer(inv -> Optional.of(QuestVersion.builder()
                .id(versionId).amenityTags("[]").cityImageIds("[]").checkpoints(List.copyOf(stored)).build()));
        when(repository.findArObjectAsset(assetId)).thenAnswer(inv -> Optional.of(QuestArObjectAsset.builder()
                .id(assetId).name("Scroll").active(true).canWander(assetCanWander).build()));
        when(repository.findNotesByCheckpoints(any())).thenReturn(List.of());
    }

    private void refuseGate() {
        doThrow(new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "AR objects are not open to you yet"))
                .when(creatorGate).requireArCreationOpen(owner);
    }

    private static SaveQuestDraftRequest.ArObjectInput input(String behavior) {
        SaveQuestDraftRequest.ArObjectInput in = new SaveQuestDraftRequest.ArObjectInput();
        in.setBehavior(behavior);
        in.setLatitude(new BigDecimal("21.02880"));
        in.setLongitude(new BigDecimal("105.8524"));
        in.setTitle("  Imperial scroll ");
        return in;
    }

    private SaveQuestDraftRequest request(SaveQuestDraftRequest.ArObjectInput object) {
        object.setAssetId(assetId);
        SaveQuestDraftRequest.CheckpointInput cp = new SaveQuestDraftRequest.CheckpointInput();
        cp.setName("Temple");
        cp.setCompletionMode("AR_OBJECT");
        cp.setArObject(object);
        SaveQuestDraftRequest request = new SaveQuestDraftRequest();
        request.setTitle("Temple walk");
        request.setCheckpoints(List.of(cp));
        return request;
    }

    private QuestCheckpoint savedCheckpoint() {
        ArgumentCaptor<QuestCheckpoint> captor = ArgumentCaptor.forClass(QuestCheckpoint.class);
        verify(repository).insertCheckpoint(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("a beta tester's object is stored with its defaults filled in")
    void betaSaves() {
        service.saveDraft(questId, owner, request(input(null)));

        QuestArObject saved = json.read(savedCheckpoint().getArObject(), QuestArObject.class, null);
        assertThat(saved.behavior()).isEqualTo("FIXED");
        assertThat(saved.anchorMode()).isEqualTo("APPROX");
        assertThat(saved.spawnRadiusM()).isEqualTo(150);
        assertThat(saved.scale()).isEqualByComparingTo("1");
        assertThat(saved.title()).isEqualTo("Imperial scroll");
    }

    @Test
    @DisplayName("anyone else is refused a new AR object")
    void othersRefused() {
        refuseGate();

        assertThatThrownBy(() -> service.saveDraft(questId, owner, request(input("FIXED"))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("not open");
    }

    @Test
    @DisplayName("an object sent back unchanged saves without the gate (the console's round trip)")
    void unchangedRoundTrip() {
        service.saveDraft(questId, owner, request(input("FIXED")));
        stored.add(savedCheckpoint());
        refuseGate();

        // The same object again, spelled differently: trailing zeros and whitespace do not count as a change.
        SaveQuestDraftRequest.ArObjectInput same = input("FIXED");
        same.setLatitude(new BigDecimal("21.0288000"));
        same.setSpawnRadiusM(150);
        service.saveDraft(questId, owner, request(same));

        SaveQuestDraftRequest.ArObjectInput moved = input("FIXED");
        moved.setLatitude(new BigDecimal("21.0290"));
        assertThatThrownBy(() -> service.saveDraft(questId, owner, request(moved)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("only an asset with a walk clip may wander")
    void wanderNeedsWalk() {
        assetCanWander = false;

        assertThatThrownBy(() -> service.saveDraft(questId, owner, request(input("WANDER"))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("cannot move");
    }

    @Test
    @DisplayName("shape is checked on save: radius range, landmark width")
    void shapeChecked() {
        SaveQuestDraftRequest.ArObjectInput tooWide = input("FIXED");
        tooWide.setSpawnRadiusM(5000);
        assertThatThrownBy(() -> service.saveDraft(questId, owner, request(tooWide)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("visibility radius");

        SaveQuestDraftRequest.ArObjectInput landmark = input("FIXED");
        landmark.setAnchorMode("IMAGE");
        SaveQuestDraftRequest.MarkerInput marker = new SaveQuestDraftRequest.MarkerInput();
        marker.setImageUrl("https://cdn/stele.jpg");
        marker.setWidthM(new BigDecimal("0.01"));
        landmark.setMarkers(List.of(marker));
        assertThatThrownBy(() -> service.saveDraft(questId, owner, request(landmark)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("width");
    }

    @Test
    @DisplayName("the draft tells the app whether it may edit AR objects; the console never may")
    void draftFlag() {
        when(creatorGate.isArCreationOpen(owner)).thenReturn(true);

        assertThat(service.getDraft(questId, owner, false).arObjectsAllowed()).isTrue();
        assertThat(service.getDraft(questId, owner, true).arObjectsAllowed()).isFalse();
    }
}
