package com.ds.goroute.quest;

import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.dto.QuestReviewDecisionRequest;
import com.ds.goroute.quest.dto.QuestReviewResultResponse;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.service.QuestReviewService;
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
@DisplayName("QuestReviewService")
class QuestReviewServiceTest {

    @Mock private QuestRepository repository;
    @Mock private com.ds.goroute.quest.service.QuestCreatorGate creatorGate;
    private QuestReviewService service;

    private final UUID questId = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();
    private final UUID draftVersionId = UUID.randomUUID();
    private final UUID creatorUser = UUID.randomUUID();
    private final UUID otherAdmin = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new QuestReviewService(repository, creatorGate, new MarketplaceJson(new ObjectMapper()));
        when(repository.findCreatorById(creatorId)).thenReturn(Optional.of(
                QuestCreatorProfile.builder().id(creatorId).userId(creatorUser).build()));
        when(repository.updatePublish(any(), anyLong(), any(), any(Boolean.class), any())).thenReturn(true);
        when(repository.updateStatus(any(), anyLong(), any(), any(), any())).thenReturn(true);
    }

    private Quest questInReview(String origin) {
        return Quest.builder().id(questId).creatorId(creatorId).origin(origin)
                .status(QuestStatus.IN_REVIEW.name()).draftVersionId(draftVersionId).dataVersion(2L).build();
    }

    private QuestReviewDecisionRequest publish() {
        return new QuestReviewDecisionRequest("PUBLISHED", "Looks good", null, null, 2L);
    }

    @Test
    @DisplayName("a different admin publishing points published_version_id at the reviewed version")
    void publishSetsPublishedVersion() {
        when(repository.findQuestById(questId)).thenReturn(Optional.of(questInReview("COMMUNITY")));

        QuestReviewResultResponse result = service.decide(questId, otherAdmin, false, publish());

        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(result.selfApproved()).isFalse();
        verify(repository).updatePublish(eq(questId), eq(2L), eq(draftVersionId), eq(false), any());
        verify(repository).insertReviewDecision(any());
    }

    @Test
    @DisplayName("the creator reviewing their own quest is refused")
    void selfReviewRefused() {
        when(repository.findQuestById(questId)).thenReturn(Optional.of(questInReview("COMMUNITY")));

        assertThatThrownBy(() -> service.decide(questId, creatorUser, false, publish()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("may not review their own");
        verify(repository, never()).updatePublish(any(), anyLong(), any(), any(Boolean.class), any());
    }

    @Test
    @DisplayName("a SUPER_ADMIN self-approving a SYSTEM quest is allowed and recorded")
    void superAdminSelfApprovesSystemQuest() {
        when(repository.findQuestById(questId)).thenReturn(Optional.of(questInReview("SYSTEM")));

        QuestReviewResultResponse result = service.decide(questId, creatorUser, true, publish());

        assertThat(result.selfApproved()).isTrue();
        verify(repository).updatePublish(eq(questId), eq(2L), eq(draftVersionId), eq(true), any());
    }

    @Test
    @DisplayName("a SUPER_ADMIN cannot self-approve a COMMUNITY quest")
    void superAdminCannotSelfApproveCommunityQuest() {
        when(repository.findQuestById(questId)).thenReturn(Optional.of(questInReview("COMMUNITY")));

        assertThatThrownBy(() -> service.decide(questId, creatorUser, true, publish()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("may not review their own");
    }

    @Test
    @DisplayName("deciding on a quest that is not in review is refused")
    void decideOnlyWhenInReview() {
        Quest draft = questInReview("COMMUNITY");
        draft.setStatus(QuestStatus.DRAFT.name());
        when(repository.findQuestById(questId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.decide(questId, otherAdmin, false, publish()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not in review");
    }

    @Test
    @DisplayName("opening a pending quest moves it to IN_REVIEW")
    void openForReview() {
        Quest pending = questInReview("COMMUNITY");
        pending.setStatus(QuestStatus.PENDING.name());
        when(repository.findQuestById(questId)).thenReturn(Optional.of(pending));

        QuestReviewResultResponse result = service.openForReview(questId, null);

        assertThat(result.status()).isEqualTo("IN_REVIEW");
        verify(repository).updateStatus(eq(questId), anyLong(), eq("IN_REVIEW"), any(), any());
    }
}
