package com.ds.goroute.quest;

import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.dto.QuestStatusChangeRequest;
import com.ds.goroute.quest.dto.QuestStatusChangeResponse;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.persistence.QuestRunRepository;
import com.ds.goroute.quest.service.QuestLifecycleRules;
import com.ds.goroute.quest.service.QuestLifecycleService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.QuestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
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
@DisplayName("Quest lifecycle: status changes and delete")
class QuestLifecycleServiceTest {

    @Mock private QuestRepository repository;
    @Mock private QuestRunRepository runRepository;
    @Mock private BusinessConfigService config;

    private QuestLifecycleRules rules;
    private QuestLifecycleService service;

    private final UUID owner = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();
    private final UUID questId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(config.getInt(BusinessConfigKey.QUEST_ARCHIVE_RESTORE_DAYS)).thenReturn(30);
        rules = new QuestLifecycleRules(config);
        service = new QuestLifecycleService(repository, runRepository, rules);
        when(repository.findCreatorById(creatorId)).thenReturn(Optional.of(
                QuestCreatorProfile.builder().id(creatorId).userId(owner).status("ACTIVE").build()));
        when(repository.updateStatus(eq(questId), anyLong(), any(), any(), any())).thenReturn(true);
        when(repository.softDeleteQuest(eq(questId), anyLong(), any())).thenReturn(true);
    }

    private Quest quest(QuestStatus status, String pausedBy, LocalDateTime updatedAt) {
        return Quest.builder().id(questId).creatorId(creatorId).origin("COMMUNITY").status(status.name())
                .publishedVersionId(UUID.randomUUID()).pausedBy(pausedBy).dataVersion(5L)
                .updatedAt(updatedAt).build();
    }

    private void stored(Quest quest) {
        when(repository.findQuestById(questId)).thenReturn(Optional.of(quest));
    }

    @Nested
    @DisplayName("rules")
    class Rules {

        @Test
        @DisplayName("a creator may pause or archive a live quest, never suspend it")
        void creatorTargetsFromPublished() {
            assertThat(rules.targets(quest(QuestStatus.PUBLISHED, null, LocalDateTime.now()), QuestStatus.Actor.CREATOR))
                    .containsExactly(QuestStatus.PAUSED, QuestStatus.ARCHIVED);
        }

        @Test
        @DisplayName("a creator lifts only their own pause; the admin lifts any")
        void pauseOwnership() {
            Quest systemPaused = quest(QuestStatus.PAUSED, "SYSTEM", LocalDateTime.now());
            assertThat(rules.targets(systemPaused, QuestStatus.Actor.CREATOR)).containsExactly(QuestStatus.ARCHIVED);
            assertThat(rules.targets(systemPaused, QuestStatus.Actor.ADMIN))
                    .containsExactly(QuestStatus.PUBLISHED, QuestStatus.SUSPENDED, QuestStatus.ARCHIVED);
            assertThat(rules.targets(quest(QuestStatus.PAUSED, "CREATOR", LocalDateTime.now()), QuestStatus.Actor.CREATOR))
                    .containsExactly(QuestStatus.PUBLISHED, QuestStatus.ARCHIVED);
        }

        @Test
        @DisplayName("an archived quest can be restored only inside the restore window")
        void restoreWindow() {
            assertThat(rules.targets(quest(QuestStatus.ARCHIVED, null, LocalDateTime.now().minusDays(10)),
                    QuestStatus.Actor.ADMIN)).containsExactly(QuestStatus.PUBLISHED);
            assertThat(rules.targets(quest(QuestStatus.ARCHIVED, null, LocalDateTime.now().minusDays(31)),
                    QuestStatus.Actor.ADMIN)).isEmpty();
        }

        @Test
        @DisplayName("the review path is not reachable through a status change")
        void reviewPathExcluded() {
            assertThat(rules.targets(quest(QuestStatus.IN_REVIEW, null, LocalDateTime.now()), QuestStatus.Actor.ADMIN))
                    .isEmpty();
            assertThat(rules.targets(quest(QuestStatus.DRAFT, null, LocalDateTime.now()), QuestStatus.Actor.CREATOR))
                    .isEmpty();
        }

        @Test
        @DisplayName("a creator deletes only DRAFT or DENIED; an admin anything not PUBLISHED")
        void deletable() {
            assertThat(rules.isDeletable(quest(QuestStatus.DENIED, null, null), QuestStatus.Actor.CREATOR)).isTrue();
            assertThat(rules.isDeletable(quest(QuestStatus.ARCHIVED, null, null), QuestStatus.Actor.CREATOR)).isFalse();
            assertThat(rules.isDeletable(quest(QuestStatus.ARCHIVED, null, null), QuestStatus.Actor.ADMIN)).isTrue();
            assertThat(rules.isDeletable(quest(QuestStatus.PUBLISHED, null, null), QuestStatus.Actor.ADMIN)).isFalse();
        }
    }

    @Nested
    @DisplayName("status change")
    class StatusChange {

        @Test
        @DisplayName("a creator pausing records CREATOR as who paused, under the version lock")
        void creatorPause() {
            stored(quest(QuestStatus.PUBLISHED, null, LocalDateTime.now()));

            QuestStatusChangeResponse result = service.changeStatusAsCreator(questId, owner,
                    new QuestStatusChangeRequest(QuestStatus.PAUSED, 5L));

            verify(repository).updateStatus(eq(questId), eq(5L), eq("PAUSED"), eq("CREATOR"), any());
            assertThat(result.status()).isEqualTo("PAUSED");
            assertThat(result.dataVersion()).isEqualTo(6L);
            assertThat(result.allowedStatuses()).containsExactly("PUBLISHED", "ARCHIVED");
        }

        @Test
        @DisplayName("someone else's quest is a 404 and nothing is written")
        void strangerGets404() {
            stored(quest(QuestStatus.PUBLISHED, null, LocalDateTime.now()));

            assertThatThrownBy(() -> service.changeStatusAsCreator(questId, stranger,
                    new QuestStatusChangeRequest(QuestStatus.PAUSED, 5L)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("not found");
            verify(repository, never()).updateStatus(any(), anyLong(), any(), any(), any());
        }

        @Test
        @DisplayName("a creator cannot suspend")
        void creatorCannotSuspend() {
            stored(quest(QuestStatus.PUBLISHED, null, LocalDateTime.now()));

            assertThatThrownBy(() -> service.changeStatusAsCreator(questId, owner,
                    new QuestStatusChangeRequest(QuestStatus.SUSPENDED, 5L)))
                    .isInstanceOf(BusinessException.class);
            verify(repository, never()).updateStatus(any(), anyLong(), any(), any(), any());
        }

        @Test
        @DisplayName("a stale version is a conflict")
        void staleVersion() {
            stored(quest(QuestStatus.PUBLISHED, null, LocalDateTime.now()));
            when(repository.updateStatus(eq(questId), eq(4L), any(), any(), any())).thenReturn(false);

            assertThatThrownBy(() -> service.changeStatusAsAdmin(questId,
                    new QuestStatusChangeRequest(QuestStatus.SUSPENDED, 4L)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("changed elsewhere");
        }

        @Test
        @DisplayName("an admin lifting a system pause clears who paused")
        void adminLiftsSystemPause() {
            stored(quest(QuestStatus.PAUSED, "SYSTEM", LocalDateTime.now()));

            service.changeStatusAsAdmin(questId, new QuestStatusChangeRequest(QuestStatus.PUBLISHED, 5L));

            verify(repository).updateStatus(eq(questId), eq(5L), eq("PUBLISHED"), eq(null), any());
        }
    }

    @Nested
    @DisplayName("admin delete")
    class AdminDelete {

        @Test
        @DisplayName("a published quest has to go offline first")
        void publishedRefused() {
            stored(quest(QuestStatus.PUBLISHED, null, LocalDateTime.now()));

            assertThatThrownBy(() -> service.deleteAsAdmin(questId, 5L)).isInstanceOf(BusinessException.class);
            verify(repository, never()).softDeleteQuest(any(), anyLong(), any());
        }

        @Test
        @DisplayName("a quest with a run in progress is kept")
        void openRunRefused() {
            stored(quest(QuestStatus.ARCHIVED, null, LocalDateTime.now()));
            when(runRepository.countRunsByStatus(questId, "IN_PROGRESS")).thenReturn(1);

            assertThatThrownBy(() -> service.deleteAsAdmin(questId, 5L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("still running");
            verify(repository, never()).softDeleteQuest(any(), anyLong(), any());
        }

        @Test
        @DisplayName("an archived quest with no open run is soft-deleted under the lock")
        void archivedDeleted() {
            stored(quest(QuestStatus.ARCHIVED, null, LocalDateTime.now()));

            service.deleteAsAdmin(questId, 5L);

            verify(repository).softDeleteQuest(eq(questId), eq(5L), any());
        }
    }
}
