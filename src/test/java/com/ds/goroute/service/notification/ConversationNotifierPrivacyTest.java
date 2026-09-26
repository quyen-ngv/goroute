package com.ds.goroute.service.notification;

import com.ds.goroute.dto.response.MarketplaceMessageResponse;
import com.ds.goroute.entity.MarketplaceConversation;
import com.ds.goroute.repository.MarketplaceChatRepository;
import com.ds.goroute.repository.NotificationRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.type.MarketplaceConversationType;
import com.ds.goroute.type.NotificationType;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What leaves the building when somebody says something.
 *
 * <p>A push payload passes through a third party and lands in a log on the device. For a
 * private thread the notification may say that there is something to read and may not say
 * what it is -- which is a property of the payload, so it is tested on the payload.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConversationNotifier")
class ConversationNotifierPrivacyTest {

    private static final UUID CONVERSATION = UUID.randomUUID();
    private static final UUID SENDER = UUID.randomUUID();
    private static final UUID READER = UUID.randomUUID();
    private static final UUID MENTIONED = UUID.randomUUID();

    @Mock private MarketplaceChatRepository conversations;
    @Mock private NotificationRepository notifications;
    @Mock private NotificationService notificationService;
    @Mock private NotificationTemplateRenderer templateRenderer;
    @Mock private UserRepository users;

    private ConversationNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new ConversationNotifier(conversations, notifications, notificationService,
                templateRenderer, users, new Gson());
        when(users.findById(any())).thenReturn(Optional.empty());
        when(templateRenderer.render(any(), anyMap(), any()))
                .thenReturn(new NotificationMessage("New message", "Somebody sent you a message"));
        when(notifications.findRecentUnreadSocialNotification(any(), any(), anyString(), any()))
                .thenReturn(Optional.empty());
        when(conversations.findNotifiableMemberIds(CONVERSATION)).thenReturn(List.of(SENDER, READER));
    }

    @Test
    @DisplayName("says a trip group has something new without saying what")
    void hidesThePreviewOfAPrivateThread() {
        when(conversations.find(CONVERSATION, null)).thenReturn(Optional.of(trip()));

        notifier.notifyNewMessage(CONVERSATION, SENDER, message("chốt 7h sáng mai nhé"));

        Map<String, Object> data = capturedPayload();
        assertThat(data).doesNotContainKey("preview");
        assertThat(data).containsEntry("conversationTitle", "Đà Lạt");
        assertThat(data).containsEntry("deepLink", "/chat/" + CONVERSATION);
    }

    @Test
    @DisplayName("hides it for a person-to-person thread as well")
    void hidesThePreviewOfADirectThread() {
        when(conversations.find(CONVERSATION, null)).thenReturn(Optional.of(direct()));

        notifier.notifyNewMessage(CONVERSATION, SENDER, message("ê"));

        assertThat(capturedPayload()).doesNotContainKey("preview");
    }

    @Test
    @DisplayName("still previews a booking thread, which is a commercial conversation")
    void keepsThePreviewOfABookingThread() {
        when(conversations.find(CONVERSATION, null)).thenReturn(Optional.of(booking()));

        notifier.notifyNewMessage(CONVERSATION, SENDER, message("Your room is ready"));

        assertThat(capturedPayload()).containsEntry("preview", "Your room is ready");
    }

    @Test
    @DisplayName("asks only for the people who did not mute it")
    void skipsMutedMembers() {
        when(conversations.find(CONVERSATION, null)).thenReturn(Optional.of(trip()));

        notifier.notifyNewMessage(CONVERSATION, SENDER, message("hi"));

        verify(conversations).findNotifiableMemberIds(CONVERSATION);
        verify(conversations, never()).findParticipants(any());
    }

    @Test
    @DisplayName("never announces a message to the person who wrote it")
    void skipsTheSender() {
        when(conversations.find(CONVERSATION, null)).thenReturn(Optional.of(trip()));

        notifier.notifyNewMessage(CONVERSATION, SENDER, message("hi"));

        verify(notificationService, never()).createNotification(eq(SENDER), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("gives somebody named in a message their own notification, not the grouped one")
    void mentionsGetTheirOwnNotification() {
        when(conversations.find(CONVERSATION, null)).thenReturn(Optional.of(trip()));
        when(conversations.findNotifiableMemberIds(CONVERSATION)).thenReturn(List.of(SENDER, READER, MENTIONED));

        notifier.notifyNewMessage(CONVERSATION, SENDER, message("@Linh xem hộ"), List.of(MENTIONED));

        verify(notificationService).createNotification(eq(MENTIONED), any(), eq(NotificationType.CHAT_MENTION),
                any(), any(), anyMap(), eq(SENDER));
        verify(notificationService, never()).createNotification(eq(MENTIONED), any(),
                eq(NotificationType.MARKETPLACE_MESSAGE), any(), any(), anyMap(), any());
        verify(notificationService).createNotification(eq(READER), any(),
                eq(NotificationType.MARKETPLACE_MESSAGE), any(), any(), anyMap(), eq(SENDER));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedPayload() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).createNotification(eq(READER), any(), any(), any(), any(),
                captor.capture(), eq(SENDER));
        return captor.getValue();
    }

    private MarketplaceMessageResponse message(String content) {
        return MarketplaceMessageResponse.builder()
                .id(UUID.randomUUID())
                .conversationId(CONVERSATION)
                .senderUserId(SENDER)
                .senderName("Linh")
                .messageType("TEXT")
                .content(content)
                .attachments(List.of())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private MarketplaceConversation trip() {
        return MarketplaceConversation.builder()
                .id(CONVERSATION)
                .conversationType(MarketplaceConversationType.TRIP.name())
                .tripId(UUID.randomUUID())
                .tripName("Đà Lạt")
                .build();
    }

    private MarketplaceConversation direct() {
        return MarketplaceConversation.builder()
                .id(CONVERSATION)
                .conversationType(MarketplaceConversationType.DIRECT.name())
                .build();
    }

    private MarketplaceConversation booking() {
        return MarketplaceConversation.builder()
                .id(CONVERSATION)
                .conversationType(MarketplaceConversationType.HOTEL_BOOKING.name())
                .organizationId(UUID.randomUUID())
                .organizationName("Khách sạn Hoa Sen")
                .bookingCode("BK-1")
                .build();
    }
}
