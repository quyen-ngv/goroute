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
}
