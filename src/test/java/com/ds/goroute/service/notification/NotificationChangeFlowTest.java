package com.ds.goroute.service.notification;

import com.ds.goroute.entity.Notification;
import com.ds.goroute.mapper.UserDeviceMapper;
import com.ds.goroute.repository.NotificationRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.UserRealtimePublisher;
import com.ds.goroute.service.external.FirebaseService;
import com.ds.goroute.service.impl.NotificationServiceImpl;
import com.ds.goroute.service.notification.event.GenericTripEvent;
import com.ds.goroute.service.notification.event.TripEvent;
import com.ds.goroute.service.notification.handler.TripNotificationHandler;
import com.ds.goroute.service.notification.strategy.AllMembersStrategy;
import com.ds.goroute.service.notification.strategy.DirectRecipientsStrategy;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.UserRealtimeEventType;
import com.google.gson.Gson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The notification list as other devices see it: every change reaches them once, pushes carry
 * the row id and never the routing lists, read-all respects a trip, and one bad recipient does
 * not cost the others their notification.
 */
@DisplayName("Notifications, as the rest of a person's devices see them")
class NotificationChangeFlowTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final UserRealtimePublisher realtime = mock(UserRealtimePublisher.class);
    private final FirebaseService firebase = mock(FirebaseService.class);
    private final NotificationChangePublisher changes = new NotificationChangePublisher(realtime, repository);
    private final NotificationServiceImpl service = new NotificationServiceImpl(
            repository, mock(UserRepository.class), firebase, new NotificationPayloadFactory(),
            mock(UserDeviceMapper.class), new Gson(), changes);

    {
        Executor direct = Runnable::run;
        ReflectionTestUtils.setField(service, "notificationExecutor", direct);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("read-all with a trip reads only that trip, and tells the other devices")
    void readAllByTrip() {
        UUID user = UUID.randomUUID();
        UUID trip = UUID.randomUUID();
        when(repository.markAllAsRead(user, trip)).thenReturn(3);
        when(repository.countUnread(user)).thenReturn(2);

        service.markAllAsRead(user, trip);

        verify(repository).markAllAsRead(user, trip);
        verify(realtime).send(eq(UserRealtimeEventType.NOTIFICATION_CHANGED), eq(user),
                eq(Map.of("unreadCount", 2)));
    }

    @Test
    @DisplayName("read-all that changed nothing says nothing")
    void readAllNoChange() {
        UUID user = UUID.randomUUID();
        when(repository.markAllAsRead(user, null)).thenReturn(0);

        service.markAllAsRead(user, null);

        verify(realtime, never()).send(any(), any(), anyMap());
    }

    @Test
    @DisplayName("a new row is announced after commit, once per person however many rows the transaction wrote")
    void onceAfterCommitPerPerson() {
        UUID user = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();

        service.createNotification(user, null, NotificationType.ADMIN_MESSAGE, "a", "b", Map.of(), null);
        service.createNotification(user, null, NotificationType.ADMIN_MESSAGE, "c", "d", Map.of(), null);
        verify(realtime, never()).send(any(), any(), anyMap());

        when(repository.countUnread(user)).thenReturn(2);
        List<TransactionSynchronization> registered = TransactionSynchronizationManager.getSynchronizations();
        registered.forEach(synchronization -> synchronization.beforeCommit(false));
        registered.forEach(TransactionSynchronization::afterCommit);

        verify(realtime, times(1)).send(eq(UserRealtimeEventType.NOTIFICATION_CHANGED), eq(user),
                eq(Map.of("unreadCount", 2)));
    }

    @Test
    @DisplayName("names the row when only one changed")
    void namesASingleRow() {
        UUID user = UUID.randomUUID();
        when(repository.countUnread(user)).thenReturn(1);

        service.createNotification(user, null, NotificationType.ADMIN_MESSAGE, "a", "b", Map.of(), null);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Map<String, Object>> payload = (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
        verify(realtime).send(eq(UserRealtimeEventType.NOTIFICATION_CHANGED), eq(user), payload.capture());
        assertThat(payload.getValue()).containsKeys("notificationId", "unreadCount")
                .containsEntry("type", "ADMIN_MESSAGE");
    }

    @Test
    @DisplayName("a trip push carries its row id and not the list of who else was told")
    void pushPayload() {
        UUID user = UUID.randomUUID();
        TripEvent event = GenericTripEvent.builder()
                .tripId(UUID.randomUUID()).actorId(UUID.randomUUID()).type(NotificationType.TRIP_UPDATED)
                .metadata(Map.of("recipientIds", List.of(user), "excludedRecipientIds", List.of(UUID.randomUUID()),
                        "deepLink", "/trip/x"))
                .build();

        service.createNotification(user, event);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Map<String, Object>> pushed = (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
        verify(firebase).sendPushToUser(eq(user), eq(NotificationType.TRIP_UPDATED), pushed.capture());
        assertThat(pushed.getValue()).doesNotContainKeys("recipientIds", "excludedRecipientIds")
                .containsKey("notificationId").containsEntry("deepLink", "/trip/x");
        ArgumentCaptor<Notification> stored = ArgumentCaptor.forClass(Notification.class);
        verify(repository).insert(stored.capture());
        assertThat(stored.getValue().getData()).doesNotContain("recipientIds").doesNotContain("notificationId");
    }

    @Test
    @DisplayName("a refreshed chat row is pushed again with its id, and announced")
    void refreshPushes() {
        UUID user = UUID.randomUUID();
        Notification row = Notification.builder().id(UUID.randomUUID()).userId(user)
                .type(NotificationType.MARKETPLACE_MESSAGE).build();

        service.refreshCoalescedNotification(row, Map.of("conversationId", "c-1"), true);

        verify(repository).updateSocialNotification(row);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Map<String, Object>> pushed = (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
        verify(firebase).sendPushToUser(eq(user), eq(NotificationType.MARKETPLACE_MESSAGE), pushed.capture());
        assertThat(pushed.getValue()).containsEntry("notificationId", row.getId().toString())
                .containsEntry("conversationId", "c-1");
        verify(realtime).send(eq(UserRealtimeEventType.NOTIFICATION_CHANGED), eq(user), anyMap());
    }

    @Test
    @DisplayName("a trip fan-out still reaches everyone after one recipient fails")
    void handlerIsolatesRecipients() {
        NotificationService notifications = mock(NotificationService.class);
        AllMembersStrategy members = mock(AllMembersStrategy.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        TripEvent event = GenericTripEvent.builder()
                .tripId(UUID.randomUUID()).type(NotificationType.TRIP_UPDATED).build();
        when(members.getRecipients(event)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("boom")).when(notifications).createNotification(first, event);

        new TripNotificationHandler(notifications, members, mock(DirectRecipientsStrategy.class)).handle(event);

        verify(notifications).createNotification(second, event);
    }

    @Test
    @DisplayName("the payload factory drops routing lists from every trip notification")
    void payloadFactoryStripsRouting() {
        TripEvent event = GenericTripEvent.builder()
                .tripId(UUID.randomUUID()).type(NotificationType.TRIP_UPDATED)
                .metadata(Map.of("recipientIds", List.of(UUID.randomUUID()), "note", "x")).build();

        assertThat(new NotificationPayloadFactory().build(event))
                .doesNotContainKey("recipientIds").containsEntry("note", "x");
    }

    @Test
    @DisplayName("reading a conversation reads its chat notifications and tells other devices")
    void conversationRead() {
        UUID user = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        when(repository.markConversationNotificationsRead(user, conversation)).thenReturn(1);

        service.markConversationNotificationsRead(user, conversation);

        verify(realtime).send(eq(UserRealtimeEventType.NOTIFICATION_CHANGED), eq(user), anyMap());
    }
}
