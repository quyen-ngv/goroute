package com.ds.goroute.meetup.service;

import com.ds.goroute.meetup.domain.Meetup;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.notification.NotificationMessage;
import com.ds.goroute.service.notification.NotificationTemplateRenderer;
import com.ds.goroute.type.NotificationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A meetup notification must never undo the change that caused it. Inside a transaction it is
 * therefore sent only after commit, in a transaction of its own: sending it inside the caller's
 * transaction let a failing (transactional) notification insert mark that transaction
 * rollback-only, so an approve/join/cancel failed at commit.
 */
@DisplayName("MeetupNotifier sends after commit, in its own transaction")
class MeetupNotifierTest {

    private final UserRepository users = mock(UserRepository.class);
    private final NotificationTemplateRenderer renderer = mock(NotificationTemplateRenderer.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final MeetupNotifier notifier = new MeetupNotifier(users, renderer, notifications, transactionManager);

    private final UUID recipientId = UUID.randomUUID();
    private final Meetup meetup = Meetup.builder().id(UUID.randomUUID()).title("đi cà phê").build();

    @BeforeEach
    void setUp() {
        when(users.findById(any())).thenReturn(Optional.empty());
        when(renderer.render(any(), anyMap(), any())).thenReturn(new NotificationMessage("title", "body"));
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("inside a transaction: nothing before commit, then one send in a REQUIRES_NEW transaction")
    void waitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();

        notifier.send(recipientId, NotificationType.MEETUP_APPROVED, meetup, "Lan", null, null, null);

        verify(notifications, never()).createNotification(any(), any(), any(), any(), any(), anyMap(), any());
        List<TransactionSynchronization> pending = TransactionSynchronizationManager.getSynchronizations();
        assertThat(pending).hasSize(1);

        pending.get(0).afterCommit();

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(notifications).createNotification(eq(recipientId), eq(null), eq(NotificationType.MEETUP_APPROVED),
                eq("title"), eq("body"), data.capture(), eq(null));
        assertThat(data.getValue()).containsEntry("meetupId", meetup.getId().toString())
                .containsEntry("meetupTitle", "đi cà phê")
                .containsEntry("deepLink", "/meetups/" + meetup.getId());
    }

    @Test
    @DisplayName("a failing send after commit is rolled back alone and swallowed")
    void afterCommitFailureIsContained() {
        TransactionSynchronizationManager.initSynchronization();
        doThrow(new IllegalStateException("insert failed")).when(notifications)
                .createNotification(any(), any(), any(), any(), any(), anyMap(), any());

        notifier.send(recipientId, NotificationType.MEETUP_CANCELLED, meetup, "Lan", null, null, null);
        TransactionSynchronization pending = TransactionSynchronizationManager.getSynchronizations().get(0);

        assertThatCode(pending::afterCommit).doesNotThrowAnyException();
        verify(transactionManager).rollback(any());
    }

    @Test
    @DisplayName("outside a transaction: sent at once, and a failure is swallowed")
    void sendsAtOnceOutsideATransaction() {
        doThrow(new IllegalStateException("insert failed")).when(notifications)
                .createNotification(any(), any(), any(), any(), any(), anyMap(), any());

        assertThatCode(() -> notifier.send(recipientId, NotificationType.MEETUP_JOIN_REQUESTED, meetup, "Lan", "Minh",
                null, null)).doesNotThrowAnyException();

        verify(notifications).createNotification(any(), any(), any(), any(), any(), anyMap(), any());
        verify(transactionManager, never()).getTransaction(any());
    }
}
