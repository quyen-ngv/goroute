package com.ds.goroute.service.external;

import com.ds.goroute.entity.UserDevice;
import com.ds.goroute.mapper.UserDeviceMapper;
import com.ds.goroute.repository.NotificationRepository;
import com.ds.goroute.service.notification.NotificationDataKeys;
import com.ds.goroute.service.notification.NotificationMessage;
import com.ds.goroute.service.notification.NotificationTemplateRenderer;
import com.ds.goroute.type.NotificationType;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirebaseService {

    /** FCM rejects a multicast carrying more than 500 tokens. */
    private static final int MULTICAST_BATCH_SIZE = 500;

    /** The one Android channel the app creates; a push naming another lands in "Miscellaneous". */
    static final String ANDROID_CHANNEL_ID = "goroute_default";

    /** Chat pushes stack per conversation on the device instead of one banner per line. */
    private static final Set<NotificationType> CHAT_TYPES =
            EnumSet.of(NotificationType.MARKETPLACE_MESSAGE, NotificationType.CHAT_MENTION);

    /**
     * Reminders that are worthless once the thing they announce has begun. A phone that was off
     * for the morning should not light up at noon with "your 9:00 tour starts in 30 minutes".
     */
    private static final Set<NotificationType> EXPIRING_REMINDERS = EnumSet.of(
            NotificationType.ITINERARY_ITEM_PREPARATION,
            NotificationType.ITINERARY_ITEM_UPCOMING,
            NotificationType.TRIP_STARTS_IN_ONE_WEEK,
            NotificationType.TRIP_STARTS_IN_THREE_DAYS,
            NotificationType.TRIP_STARTS_IN_TWO_DAYS,
            NotificationType.TRIP_STARTS_IN_ONE_DAY,
            NotificationType.TRIP_STARTS_IN_TWO_HOURS);
    static final Duration MIN_REMINDER_TTL = Duration.ofSeconds(60);
    static final Duration MAX_REMINDER_TTL = Duration.ofHours(24);

    private final UserDeviceMapper userDeviceMapper;
    private final NotificationTemplateRenderer templateRenderer;
    private final NotificationRepository notificationRepository;

    public boolean sendPushToUser(UUID userId, NotificationType type, Map<String, Object> data) {
        List<UserDevice> devices = userDeviceMapper.findActiveByUserId(userId);

        if (devices.isEmpty()) {
            log.warn("No active devices found for user: {}", userId);
            return false;
        }

        // One FCM round trip per language instead of one per device: the rendered title and body
        // are the only thing a device's language changes, so devices that share a language share
        // a message and differ only by token. A user with three devices used to cost three
        // blocking HTTP calls; it now costs one.
        PushOptions options = optionsFor(userId, type, data);
        boolean sent = false;
        for (Map.Entry<String, List<UserDevice>> group : groupByLanguage(devices).entrySet()) {
            sent |= sendToLanguageGroup(type, data, options, group.getKey(), group.getValue());
        }
        return sent;
    }

    private Map<String, List<UserDevice>> groupByLanguage(List<UserDevice> devices) {
        Map<String, List<UserDevice>> grouped = new LinkedHashMap<>();
        for (UserDevice device : devices) {
            if (device.getFcmToken() == null || device.getFcmToken().isBlank()) {
                log.warn("Skipping device {} with no FCM token", device.getId());
                continue;
            }
            grouped.computeIfAbsent(device.getLanguage() == null ? "" : device.getLanguage(),
                    language -> new ArrayList<>()).add(device);
        }
        return grouped;
    }

    private boolean sendToLanguageGroup(NotificationType type,
                                        Map<String, Object> data,
                                        PushOptions options,
                                        String language,
                                        List<UserDevice> devices) {
        NotificationMessage message;
        try {
            message = templateRenderer.render(type, data, language.isEmpty() ? null : language);
        } catch (Exception e) {
            log.error("Failed to render push {} for language {}: {}", type, language, e.getMessage(), e);
            return false;
        }
        boolean sent = false;
        // FCM refuses a multicast of more than 500 tokens.
        for (int start = 0; start < devices.size(); start += MULTICAST_BATCH_SIZE) {
            List<UserDevice> batch = devices.subList(start, Math.min(devices.size(), start + MULTICAST_BATCH_SIZE));
            sent |= sendMulticast(batch, message, data, options);
        }
        return sent;
    }

    /**
     * Sends one message to many tokens and reports every token's outcome separately, so a
     * single dead token still only kills its own delivery -- the same isolation the per-device
     * loop gave, minus the per-device round trip.
     */
    private boolean sendMulticast(List<UserDevice> devices, NotificationMessage message,
                                  Map<String, Object> data, PushOptions options) {
        List<String> tokens = devices.stream().map(UserDevice::getFcmToken).toList();
        MulticastMessage.Builder builder = MulticastMessage.builder().addAllTokens(tokens);
        applyContent(builder::setNotification, builder::setAndroidConfig, builder::setApnsConfig,
                message.title(), message.body(), data, options);
        Map<String, String> wireData = toWireData(data);
        if (!wireData.isEmpty()) {
            builder.putAllData(wireData);
        }

        BatchResponse response;
        try {
            response = FirebaseMessaging.getInstance().sendEachForMulticast(builder.build());
        } catch (Exception e) {
            log.error("Error sending FCM multicast to {} device(s): {}", tokens.size(), e.getMessage(), e);
            return false;
        }

        List<SendResponse> results = response.getResponses();
        for (int index = 0; index < results.size() && index < devices.size(); index++) {
            SendResponse result = results.get(index);
            if (result.isSuccessful()) {
                continue;
            }
            FirebaseMessagingException error = result.getException();
            log.error("Failed to send push to device {}: {}", devices.get(index).getId(),
                    error == null ? "unknown error" : error.getMessage(), error);
            if (error != null && isDeadToken(error.getMessagingErrorCode())) {
                userDeviceMapper.deleteByToken(tokens.get(index));
                log.info("Deleted dead FCM token of device {}", devices.get(index).getId());
            }
        }
        log.info("Sent FCM multicast: success={} failure={}",
                response.getSuccessCount(), response.getFailureCount());
        return response.getSuccessCount() > 0;
    }

    /**
     * Per-push delivery settings that do not depend on the device's language.
     *
     * @param collapseKey  groups pushes about one conversation (Android collapse key and tag, APNs thread)
     * @param ttl          how long FCM may hold the push for an offline device; null for its default
     * @param badge        the app icon count on iOS; null leaves it unchanged
     */
    record PushOptions(String collapseKey, Duration ttl, Integer badge) {
        static final PushOptions NONE = new PushOptions(null, null, null);
    }

    PushOptions optionsFor(UUID userId, NotificationType type, Map<String, Object> data) {
        String collapseKey = null;
        if (type != null && CHAT_TYPES.contains(type) && data != null
                && data.get(NotificationDataKeys.CONVERSATION_ID) != null) {
            collapseKey = String.valueOf(data.get(NotificationDataKeys.CONVERSATION_ID));
        }
        Duration ttl = type != null && EXPIRING_REMINDERS.contains(type)
                ? reminderTtl(data, Instant.now())
                : null;
        return new PushOptions(collapseKey, ttl, unreadBadge(userId));
    }

    /** Time left until the reminded thing starts, clamped to 60 s .. 24 h; null when unknown. */
    static Duration reminderTtl(Map<String, Object> data, Instant now) {
        Object startsAt = data == null ? null : data.get(NotificationDataKeys.STARTS_AT);
        if (startsAt == null) {
            return null;
        }
        try {
            Duration left = Duration.between(now, OffsetDateTime.parse(String.valueOf(startsAt)).toInstant());
            if (left.compareTo(MIN_REMINDER_TTL) < 0) return MIN_REMINDER_TTL;
            if (left.compareTo(MAX_REMINDER_TTL) > 0) return MAX_REMINDER_TTL;
            return left;
        } catch (DateTimeParseException malformed) {
            return null;
        }
    }

    /**
     * One indexed count per push (not per device), read after the row it announces committed.
     * A failure only costs the badge, never the push.
     */
    private Integer unreadBadge(UUID userId) {
        if (userId == null) return null;
        try {
            return Math.max(0, notificationRepository.countUnread(userId));
        } catch (RuntimeException exception) {
            log.debug("Could not count unread notifications for the badge: {}", exception.getMessage());
            return null;
        }
    }

    /** Builds the notification, Android and APNs blocks shared by single and multicast sends. */
    private void applyContent(Consumer<Notification> notification,
                              Consumer<AndroidConfig> android,
                              Consumer<ApnsConfig> apns,
                              String title,
                              String body,
                              Map<String, Object> data,
                              PushOptions options) {
        String imageUrl = imageUrlFrom(data);
        Notification.Builder notificationBuilder = Notification.builder()
                .setTitle(title)
                .setBody(body);
        if (imageUrl != null) {
            notificationBuilder.setImage(imageUrl);
        }

        AndroidNotification.Builder androidNotificationBuilder = AndroidNotification.builder()
                .setTitle(title)
                .setBody(body)
                .setChannelId(ANDROID_CHANNEL_ID);
        if (imageUrl != null) {
            androidNotificationBuilder.setImage(imageUrl);
        }
        if (options.collapseKey() != null) {
            // Same tag replaces the previous banner of the conversation instead of stacking.
            androidNotificationBuilder.setTag(options.collapseKey());
        }
        AndroidConfig.Builder androidConfigBuilder = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH);
        if (options.collapseKey() != null) {
            androidConfigBuilder.setCollapseKey(options.collapseKey());
        }
        if (options.ttl() != null) {
            androidConfigBuilder.setTtl(options.ttl().toMillis());
        }

        Aps.Builder apsBuilder = Aps.builder()
                .setAlert(ApsAlert.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .setSound("default");
        if (options.collapseKey() != null) {
            apsBuilder.setThreadId(options.collapseKey());
        }
        if (options.badge() != null) {
            apsBuilder.setBadge(options.badge());
        }
        ApnsConfig.Builder apnsConfigBuilder = ApnsConfig.builder()
                .putHeader("apns-priority", "10")
                .putHeader("apns-push-type", "alert");
        if (options.collapseKey() != null) {
            apnsConfigBuilder.putHeader("apns-collapse-id", options.collapseKey());
        }
        if (options.ttl() != null) {
            // APNs reads an absolute expiry, in epoch seconds.
            apnsConfigBuilder.putHeader("apns-expiration",
                    String.valueOf(Instant.now().plus(options.ttl()).getEpochSecond()));
        }
        if (imageUrl != null) {
            // iOS only downloads the FCM image attachment when the payload opts into
            // mutable content and points APNs at the image through fcm_options.
            apsBuilder.setMutableContent(true);
            apnsConfigBuilder.setFcmOptions(
                    ApnsFcmOptions.builder().setImage(imageUrl).build());
        }

        notification.accept(notificationBuilder.build());
        android.accept(androidConfigBuilder
                .setNotification(androidNotificationBuilder.build())
                .build());
        apns.accept(apnsConfigBuilder.setAps(apsBuilder.build()).build());
    }

    public void sendPush(String fcmToken, String title, String body, Map<String, Object> data) {
        try {
            Message.Builder messageBuilder = Message.builder().setToken(fcmToken);
            applyContent(messageBuilder::setNotification, messageBuilder::setAndroidConfig,
                    messageBuilder::setApnsConfig, title, body, data, PushOptions.NONE);

            Map<String, String> wireData = toWireData(data);
            if (!wireData.isEmpty()) {
                messageBuilder.putAllData(wireData);
            }

            String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
            log.info("Successfully sent FCM message: {}", response);
        } catch (FirebaseMessagingException e) {
            log.error("Error sending FCM message: {}", e.getMessage(), e);
            if (isDeadToken(e.getMessagingErrorCode())) {
                userDeviceMapper.deleteByToken(fcmToken);
                log.info("Deleted dead FCM token");
            }
            throw new RuntimeException("Failed to send push notification", e);
        }
    }

    private String imageUrlFrom(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        Object value = data.get("imageUrl");
        if (value == null) {
            return null;
        }
        String imageUrl = String.valueOf(value).trim();
        return imageUrl.isEmpty() ? null : imageUrl;
    }

    /**
     * Only answers that say the token itself will never work again. INVALID_ARGUMENT is usually
     * our own payload (an oversized data field, a bad TTL) and would otherwise make every device
     * of a person disappear because of one malformed message.
     */
    static boolean isDeadToken(MessagingErrorCode code) {
        return code == MessagingErrorCode.UNREGISTERED
                || code == MessagingErrorCode.SENDER_ID_MISMATCH;
    }

    /**
     * The data block as FCM carries it: strings only, no nulls, and never the routing lists that
     * say who else was told.
     */
    static Map<String, String> toWireData(Map<String, Object> data) {
        Map<String, String> wire = new LinkedHashMap<>();
        if (data == null) {
            return wire;
        }
        data.forEach((key, value) -> {
            if (key == null || value == null || NotificationDataKeys.ROUTING_ONLY.contains(key)) {
                return;
            }
            wire.put(key, String.valueOf(value));
        });
        return wire;
    }
}
