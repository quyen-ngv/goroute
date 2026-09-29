package com.ds.goroute.service.external;

import com.ds.goroute.mapper.UserDeviceMapper;
import com.ds.goroute.repository.NotificationRepository;
import com.ds.goroute.service.notification.NotificationTemplateRenderer;
import com.ds.goroute.type.NotificationType;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** What a push carries and when a token is given up on. */
@DisplayName("FirebaseService push rules")
class FirebaseServicePushRulesTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final FirebaseService service = new FirebaseService(
            mock(UserDeviceMapper.class), mock(NotificationTemplateRenderer.class), notifications);

    @Test
    @DisplayName("forgets a token only when FCM says it will never work again")
    void deletesOnlyDeadTokens() {
        assertThat(FirebaseService.isDeadToken(MessagingErrorCode.UNREGISTERED)).isTrue();
        assertThat(FirebaseService.isDeadToken(MessagingErrorCode.SENDER_ID_MISMATCH)).isTrue();
        assertThat(FirebaseService.isDeadToken(MessagingErrorCode.INVALID_ARGUMENT)).isFalse();
        assertThat(FirebaseService.isDeadToken(MessagingErrorCode.UNAVAILABLE)).isFalse();
        assertThat(FirebaseService.isDeadToken(null)).isFalse();
    }

    @Test
    @DisplayName("never sends who else was told, nor null values")
    void stripsRoutingLists() {
        Map<String, Object> data = new HashMap<>();
        data.put("recipientIds", List.of(UUID.randomUUID()));
        data.put("excludedRecipientIds", List.of(UUID.randomUUID()));
        data.put("tripId", "t-1");
        data.put("preview", null);

        assertThat(FirebaseService.toWireData(data)).containsOnlyKeys("tripId");
    }

    @Test
    @DisplayName("collapses chat pushes by conversation and badges with the unread count")
    void chatOptions() {
        UUID user = UUID.randomUUID();
        when(notifications.countUnread(user)).thenReturn(4);

        FirebaseService.PushOptions options = service.optionsFor(user, NotificationType.MARKETPLACE_MESSAGE,
                Map.of("conversationId", "c-1"));

        assertThat(options.collapseKey()).isEqualTo("c-1");
        assertThat(options.badge()).isEqualTo(4);
        assertThat(options.ttl()).isNull();
    }

    @Test
    @DisplayName("does not collapse other types, and still pushes when the badge count fails")
    void otherTypes() {
        UUID user = UUID.randomUUID();
        when(notifications.countUnread(user)).thenThrow(new IllegalStateException("db"));

        FirebaseService.PushOptions options = service.optionsFor(user, NotificationType.TRIP_UPDATED,
                Map.of("conversationId", "c-1"));

        assertThat(options.collapseKey()).isNull();
        assertThat(options.badge()).isNull();
    }

    @Test
    @DisplayName("lets a reminder expire when the thing it announces starts, within 60 s .. 24 h")
    void reminderTtl() {
        Instant now = Instant.parse("2026-09-29T08:00:00Z");
        assertThat(FirebaseService.reminderTtl(startsAt(now.plus(Duration.ofMinutes(30))), now))
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(FirebaseService.reminderTtl(startsAt(now.plusSeconds(5)), now)).isEqualTo(Duration.ofSeconds(60));
        assertThat(FirebaseService.reminderTtl(startsAt(now.plus(Duration.ofDays(7))), now)).isEqualTo(Duration.ofHours(24));
        assertThat(FirebaseService.reminderTtl(Map.of(), now)).isNull();
        assertThat(FirebaseService.reminderTtl(Map.of("startsAt", "tomorrow"), now)).isNull();
    }

    @Test
    @DisplayName("applies the reminder lifetime only to reminders")
    void ttlOnlyForReminders() {
        Map<String, Object> data = startsAt(Instant.now().plus(Duration.ofHours(1)));
        assertThat(service.optionsFor(null, NotificationType.ITINERARY_ITEM_UPCOMING, data).ttl()).isNotNull();
        assertThat(service.optionsFor(null, NotificationType.ITINERARY_ITEM_COMPLETED, data).ttl()).isNull();
    }

    private Map<String, Object> startsAt(Instant instant) {
        return Map.of("startsAt", instant.atOffset(ZoneOffset.ofHours(7)).toString());
    }
}
