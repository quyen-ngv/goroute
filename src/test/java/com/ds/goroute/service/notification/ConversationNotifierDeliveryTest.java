package com.ds.goroute.service.notification;

import com.ds.goroute.dto.response.MarketplaceMessageResponse;
import com.ds.goroute.entity.MarketplaceConversation;
import com.ds.goroute.entity.MarketplaceMemberInboxState;
import com.ds.goroute.entity.Notification;
import com.ds.goroute.repository.MarketplaceChatRepository;
import com.ds.goroute.repository.NotificationRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.type.MarketplaceConversationType;
import com.ds.goroute.type.NotificationType;
import com.google.gson.Gson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * When and how a message is announced: only after it committed, one recipient's failure never
 * the next one's, and every message rings even when the list row is coalesced.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConversationNotifier delivery")
class ConversationNotifierDeliveryTest {

    private static final UUID CONVERSATION = UUID.randomUUID();
    private static final UUID SENDER = UUID.randomUUID();
    private static final UUID READER = UUID.randomUUID();
    private static final UUID OTHER_READER = UUID.randomUUID();

    @Mock private MarketplaceChatRepository conversations;
    @Mock private NotificationRepository notifications;
    @Mock private NotificationService notificationService;
    @Mock private NotificationTemplateRenderer templateRenderer;
    @Mock private UserRepository users;
    @Mock private PlatformTransactionManager transactionManager;

    private ConversationNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new ConversationNotifier(conversations, notifications, notificationService,
                templateRenderer, users, new Gson(), transactionManager, Runnable::run);
        when(users.findById(any())).thenReturn(Optional.empty());
        when(templateRenderer.render(any(), anyMap(), any())).thenReturn(new NotificationMessage("t", "b"));
        when(notifications.findRecentUnreadSocialNotification(any(), any(), anyString(), any()))
                .thenReturn(Optional.empty());
        when(conversations.find(CONVERSATION, null)).thenReturn(Optional.of(MarketplaceConversation.builder()
                .id(CONVERSATION).conversationType(MarketplaceConversationType.TRIP.name())
                .tripId(UUID.randomUUID()).tripName("Huế").build()));
        when(conversations.findNotifiableMemberIds(CONVERSATION)).thenReturn(List.of(SENDER, READER, OTHER_READER));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("waits for the message to commit, and says nothing about one that rolled back")
    void runsAfterCommitOnly() {
        TransactionSynchronizationManager.initSynchronization();

        notifier.notifyNewMessage(CONVERSATION, SENDER, message(3L));

        verifyNoInteractions(notificationService);
        List<TransactionSynchronization> registered = TransactionSynchronizationManager.getSynchronizations();
        assertThat(registered).hasSize(1);

        registered.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verifyNoInteractions(notificationService);

        registered.get(0).afterCommit();
        verify(notificationService).createNotification(eq(READER), any(), eq(NotificationType.MARKETPLACE_MESSAGE),
                any(), any(), anyMap(), eq(SENDER));
    }

    @Test
    @DisplayName("keeps telling the others when one recipient fails, and never throws")
    void isolatesRecipients() {
        doThrow(new IllegalStateException("db hiccup")).when(notificationService).createNotification(
                eq(READER), any(), any(), any(), any(), anyMap(), any());

        assertThatCode(() -> notifier.notifyNewMessage(CONVERSATION, SENDER, message(3L))).doesNotThrowAnyException();

        verify(notificationService).createNotification(eq(OTHER_READER), any(), eq(NotificationType.MARKETPLACE_MESSAGE),
                any(), any(), anyMap(), eq(SENDER));
    }

    @Test
    @DisplayName("rings for every message, refreshing the waiting row instead of adding one")
    void pushesEveryMessage() {
        Notification waiting = Notification.builder().id(UUID.randomUUID()).userId(READER)
                .type(NotificationType.MARKETPLACE_MESSAGE).data("{\"sequenceNo\":2}").build();
        when(notifications.findRecentUnreadSocialNotification(eq(READER), eq(NotificationType.MARKETPLACE_MESSAGE),
                eq("CONVERSATION"), eq(CONVERSATION))).thenReturn(Optional.of(waiting));

        notifier.notifyNewMessage(CONVERSATION, SENDER, message(3L));

        ArgumentCaptor<Map<String, Object>> data = dataCaptor();
        verify(notificationService).refreshCoalescedNotification(eq(waiting), data.capture(), eq(true));
        assertThat(data.getValue()).containsEntry("sequenceNo", 3L)
                .containsEntry(NotificationDataKeys.CONVERSATION_ID, CONVERSATION.toString());
        verify(notificationService, never()).createNotification(eq(READER), any(), any(), any(), any(), anyMap(), any());
        verify(notifications).lockTarget(READER, NotificationType.MARKETPLACE_MESSAGE, "CONVERSATION", CONVERSATION);
    }

    @Test
    @DisplayName("does not announce a message the reader already read")
    void skipsReadersWhoAlreadyReadIt() {
        when(conversations.findMemberInboxStates(CONVERSATION)).thenReturn(List.of(
                MarketplaceMemberInboxState.builder().userId(READER).readSequenceNo(3L).unreadCount(0L).build()));

        notifier.notifyNewMessage(CONVERSATION, SENDER, message(3L));

        verify(notificationService, never()).createNotification(eq(READER), any(), any(), any(), any(), anyMap(), any());
        verify(notificationService).createNotification(eq(OTHER_READER), any(), any(), any(), any(), anyMap(), any());
    }

    @Test
    @DisplayName("marks a conversation's chat notifications read when the reader catches up")
    void readingClearsTheNotifications() {
        notifier.conversationRead(READER, CONVERSATION);

        verify(notificationService).markConversationNotificationsRead(READER, CONVERSATION);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Map<String, Object>> dataCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }

    private MarketplaceMessageResponse message(long sequenceNo) {
        return MarketplaceMessageResponse.builder()
                .id(UUID.randomUUID()).conversationId(CONVERSATION).senderUserId(SENDER).senderName("An")
                .messageType("TEXT").content("hello").attachments(List.of()).sequenceNo(sequenceNo)
                .createdAt(LocalDateTime.now()).build();
    }
}
