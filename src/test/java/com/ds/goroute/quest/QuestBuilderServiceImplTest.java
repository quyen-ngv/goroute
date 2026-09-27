package com.ds.goroute.quest;

import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.domain.QuestVersion;
import com.ds.goroute.quest.dto.SaveQuestDraftRequest;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.service.QuestBuilderServiceImpl;
import com.ds.goroute.quest.service.QuestDraftValidator;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.ds.goroute.type.QuestStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QuestBuilderServiceImpl guards")
class QuestBuilderServiceImplTest {

    @Mock private QuestRepository repository;
    @Mock private QuestDraftValidator validator;
    @Mock private com.ds.goroute.quest.service.QuestCreatorGate creatorGate;

    private QuestBuilderServiceImpl service;

    private final UUID owner = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();
    private final UUID questId = UUID.randomUUID();
    private final UUID draftVersionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new QuestBuilderServiceImpl(repository, validator, creatorGate, new MarketplaceJson(new ObjectMapper()));
    }

    private Quest draftQuest() {
        return Quest.builder().id(questId).creatorId(creatorId).origin("COMMUNITY")
                .status(QuestStatus.DRAFT.name()).draftVersionId(draftVersionId).dataVersion(3L).build();
    }

    private QuestCreatorProfile creator(UUID userId) {
        return QuestCreatorProfile.builder().id(creatorId).userId(userId).status("ACTIVE").build();
    }

    @Test
    @DisplayName("a stranger asking for someone else's draft gets a 404, not a 403")
    void strangerGets404() {
        when(repository.findQuestById(questId)).thenReturn(Optional.of(draftQuest()));
        when(repository.findCreatorById(creatorId)).thenReturn(Optional.of(creator(owner)));

        assertThatThrownBy(() -> service.getDraft(questId, stranger, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("a save against a stale version is a conflict, and nothing is written")
    void staleSaveIsConflict() {
        when(repository.findQuestById(questId)).thenReturn(Optional.of(draftQuest()));
        when(repository.findCreatorById(creatorId)).thenReturn(Optional.of(creator(owner)));
        when(repository.updateStatus(eq(questId), anyLong(), eq("DRAFT"), any(), any())).thenReturn(false);

        SaveQuestDraftRequest request = new SaveQuestDraftRequest();
        request.setExpectedVersion(2);

        assertThatThrownBy(() -> service.saveDraft(questId, owner, request))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).clearVersionContent(any());
    }

    @Test
    @DisplayName("the creator gate can refuse a submit before validation")
    void gateRefusesSubmit() {
        when(repository.findQuestById(questId)).thenReturn(Optional.of(draftQuest()));
        when(repository.findCreatorById(creatorId)).thenReturn(Optional.of(creator(owner)));
        org.mockito.Mockito.doThrow(new BusinessException(com.ds.goroute.constant.ErrorConstant.ALREADY_PROCESSED,
                "You already have a quest awaiting review")).when(creatorGate).requireCanSubmit(any());

        assertThatThrownBy(() -> service.submit(questId, owner, null, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("awaiting review");
        verify(validator, never()).validateForSubmit(any());
        verify(repository, never()).updateStatus(any(), anyLong(), eq("PENDING"), any(), any());
    }

    @Test
    @DisplayName("a valid submit flips the quest to PENDING and records the submission")
    void validSubmitGoesPending() {
        when(repository.findCreatorById(creatorId)).thenReturn(Optional.of(creator(owner)));
        when(repository.updateStatus(eq(questId), anyLong(), eq("PENDING"), any(), any())).thenReturn(true);
        Quest pending = draftQuest();
        pending.setStatus(QuestStatus.PENDING.name());
        when(repository.findQuestById(questId)).thenReturn(Optional.of(draftQuest()), Optional.of(pending));
        when(repository.loadVersionGraph(any()))
                .thenReturn(Optional.of(QuestVersion.builder().id(draftVersionId).amenityTags("[]").build()));
        when(repository.findNotesByCheckpoints(any())).thenReturn(List.of());

        service.submit(questId, owner, null, true);

        verify(creatorGate).requireCanSubmit(any());
        verify(validator).validateForSubmit(any());
        verify(repository).updateStatus(eq(questId), anyLong(), eq("PENDING"), any(), any());
        verify(creatorGate).recordSubmission(any());
    }

    @Test
    @DisplayName("the owner can delete a draft, guarded by its version")
    void ownerDeletesDraft() {
        when(repository.findQuestById(questId)).thenReturn(Optional.of(draftQuest()));
        when(repository.findCreatorById(creatorId)).thenReturn(Optional.of(creator(owner)));
        when(repository.softDeleteQuest(eq(questId), eq(3L), any())).thenReturn(true);

        service.delete(questId, owner, 3L);

        verify(repository).softDeleteQuest(eq(questId), eq(3L), any());
    }

    @Test
    @DisplayName("a quest under review cannot be deleted by its creator")
    void pendingQuestCannotBeDeleted() {
        Quest pending = draftQuest();
        pending.setStatus(QuestStatus.PENDING.name());
        when(repository.findQuestById(questId)).thenReturn(Optional.of(pending));
        when(repository.findCreatorById(creatorId)).thenReturn(Optional.of(creator(owner)));

        assertThatThrownBy(() -> service.delete(questId, owner, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be edited");
        verify(repository, never()).softDeleteQuest(any(), anyLong(), any());
    }

    @Test
    @DisplayName("deleting against a stale version is a conflict")
    void staleDeleteIsConflict() {
        when(repository.findQuestById(questId)).thenReturn(Optional.of(draftQuest()));
        when(repository.findCreatorById(creatorId)).thenReturn(Optional.of(creator(owner)));
        when(repository.softDeleteQuest(eq(questId), anyLong(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.delete(questId, owner, 2L))
                .isInstanceOf(BusinessException.class);
    }

    private SaveQuestDraftRequest requestWithImages(List<String> urls) {
        SaveQuestDraftRequest.CheckpointInput checkpoint = new SaveQuestDraftRequest.CheckpointInput();
        checkpoint.setName("Tháp Rùa");
        checkpoint.setImageUrls(new java.util.ArrayList<>(urls));
        SaveQuestDraftRequest request = new SaveQuestDraftRequest();
        request.setExpectedVersion(3);
        request.setCheckpoints(new java.util.ArrayList<>(List.of(checkpoint)));
        return request;
    }

    private void stubEditableDraft() {
        when(repository.findQuestById(questId)).thenReturn(Optional.of(draftQuest()));
        when(repository.findCreatorById(creatorId)).thenReturn(Optional.of(creator(owner)));
        when(repository.updateStatus(eq(questId), anyLong(), eq("DRAFT"), any(), any())).thenReturn(true);
        when(repository.findVersionById(draftVersionId))
                .thenReturn(Optional.of(QuestVersion.builder().id(draftVersionId).build()));
        when(repository.loadVersionGraph(draftVersionId))
                .thenReturn(Optional.of(QuestVersion.builder().id(draftVersionId).amenityTags("[]").build()));
    }

    @Test
    @DisplayName("checkpoint photos are stored as a JSON array, blanks dropped")
    void checkpointPhotosAreStored() {
        stubEditableDraft();

        service.saveDraft(questId, owner, requestWithImages(List.of("https://cdn/a.jpg", " ", "https://cdn/b.jpg")));

        org.mockito.ArgumentCaptor<com.ds.goroute.quest.domain.QuestCheckpoint> saved =
                org.mockito.ArgumentCaptor.forClass(com.ds.goroute.quest.domain.QuestCheckpoint.class);
        verify(repository).insertCheckpoint(saved.capture());
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getImageUrls())
                .isEqualTo("[\"https://cdn/a.jpg\",\"https://cdn/b.jpg\"]");
    }

    @Test
    @DisplayName("more than ten photos on one checkpoint is refused (the save transaction rolls back)")
    void tooManyCheckpointPhotosRefused() {
        stubEditableDraft();
        List<String> urls = java.util.stream.IntStream.range(0, 11).mapToObj(i -> "https://cdn/" + i + ".jpg").toList();

        assertThatThrownBy(() -> service.saveDraft(questId, owner, requestWithImages(urls)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("more than 10 photos");
        verify(repository, never()).insertCheckpoint(any());
    }

    @Test
    @DisplayName("D18: saving a published quest writes a new live version and never rewrites the one players run")
    void publishedSaveIsANewLiveVersion() {
        UUID liveVersionId = UUID.randomUUID();
        Quest live = draftQuest();
        live.setStatus(QuestStatus.PUBLISHED.name());
        live.setPublishedVersionId(liveVersionId);
        live.setDraftVersionId(liveVersionId);
        when(repository.findQuestById(questId)).thenReturn(Optional.of(live));
        when(repository.findCreatorById(creatorId)).thenReturn(Optional.of(creator(owner)));
        when(repository.findVersionById(liveVersionId))
                .thenReturn(Optional.of(QuestVersion.builder().id(liveVersionId).version(2).contentLanguage("vi").build()));
        when(repository.publishEdit(eq(questId), eq(3L), any(), any())).thenReturn(true);
        when(repository.loadVersionGraph(any()))
                .thenReturn(Optional.of(QuestVersion.builder().id(liveVersionId).amenityTags("[]").build()));

        service.saveDraft(questId, owner, requestWithImages(List.of()));

        org.mockito.ArgumentCaptor<QuestVersion> inserted = org.mockito.ArgumentCaptor.forClass(QuestVersion.class);
        verify(repository).insertVersion(inserted.capture());
        org.assertj.core.api.Assertions.assertThat(inserted.getValue().getVersion()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(inserted.getValue().getId()).isNotEqualTo(liveVersionId);
        verify(repository).publishEdit(eq(questId), eq(3L), eq(inserted.getValue().getId()), any());
        verify(repository, never()).clearVersionContent(any());
        verify(repository, never()).updateStatus(any(), anyLong(), any(), any(), any());
        verify(validator).validateForSubmit(any());
    }

    @Test
    @DisplayName("a checkpoint category outside the trip activity list is refused")
    void unknownCheckpointCategoryRefused() {
        stubEditableDraft();
        SaveQuestDraftRequest request = requestWithImages(List.of());
        request.getCheckpoints().get(0).setCategory("casino");

        assertThatThrownBy(() -> service.saveDraft(questId, owner, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("unknown category");
    }
}
