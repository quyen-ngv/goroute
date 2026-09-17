package com.ds.goroute.service.external;

import com.ds.goroute.entity.UserDevice;
import com.ds.goroute.mapper.UserDeviceMapper;
import com.ds.goroute.service.notification.NotificationMessage;
import com.ds.goroute.service.notification.NotificationTemplateRenderer;
import com.ds.goroute.type.NotificationType;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirebaseService {

    /** FCM rejects a multicast carrying more than 500 tokens. */
    private static final int MULTICAST_BATCH_SIZE = 500;

    private final UserDeviceMapper userDeviceMapper;
    private final NotificationTemplateRenderer templateRenderer;

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
        boolean sent = false;
        for (Map.Entry<String, List<UserDevice>> group : groupByLanguage(devices).entrySet()) {
            sent |= sendToLanguageGroup(type, data, group.getKey(), group.getValue());
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
            sent |= sendMulticast(batch, message, data);
        }
        return sent;
    }

    /**
     * Sends one message to many tokens and reports every token's outcome separately, so a
     * single dead token still only kills its own delivery -- the same isolation the per-device
     * loop gave, minus the per-device round trip.
     */
    private boolean sendMulticast(List<UserDevice> devices, NotificationMessage message, Map<String, Object> data) {
        List<String> tokens = devices.stream().map(UserDevice::getFcmToken).toList();
        MulticastMessage.Builder builder = MulticastMessage.builder().addAllTokens(tokens);
        applyContent(builder::setNotification, builder::setAndroidConfig, builder::setApnsConfig,
                message.title(), message.body(), data);
        if (data != null && !data.isEmpty()) {
            builder.putAllData(convertToStringMap(data));
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
            if (error != null && isInvalidToken(error)) {
                userDeviceMapper.deleteByToken(tokens.get(index));
                log.info("Deleted invalid FCM token");
            }
        }
        log.info("Sent FCM multicast: success={} failure={}",
                response.getSuccessCount(), response.getFailureCount());
        return response.getSuccessCount() > 0;
    }

    /** Builds the notification, Android and APNs blocks shared by single and multicast sends. */
    private void applyContent(Consumer<Notification> notification,
                              Consumer<AndroidConfig> android,
                              Consumer<ApnsConfig> apns,
                              String title,
                              String body,
                              Map<String, Object> data) {
        String imageUrl = imageUrlFrom(data);
        Notification.Builder notificationBuilder = Notification.builder()
                .setTitle(title)
                .setBody(body);
        if (imageUrl != null) {
            notificationBuilder.setImage(imageUrl);
        }

        AndroidNotification.Builder androidNotificationBuilder = AndroidNotification.builder()
                .setTitle(title)
                .setBody(body);
        if (imageUrl != null) {
            androidNotificationBuilder.setImage(imageUrl);
        }

        Aps.Builder apsBuilder = Aps.builder()
                .setAlert(ApsAlert.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .setSound("default");
        ApnsConfig.Builder apnsConfigBuilder = ApnsConfig.builder()
                .putHeader("apns-priority", "10")
                .putHeader("apns-push-type", "alert");
        if (imageUrl != null) {
            // iOS only downloads the FCM image attachment when the payload opts into
            // mutable content and points APNs at the image through fcm_options.
            apsBuilder.setMutableContent(true);
            apnsConfigBuilder.setFcmOptions(
                    ApnsFcmOptions.builder().setImage(imageUrl).build());
        }

        notification.accept(notificationBuilder.build());
        android.accept(AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(androidNotificationBuilder.build())
                .build());
        apns.accept(apnsConfigBuilder.setAps(apsBuilder.build()).build());
    }

    public void sendPush(String fcmToken, String title, String body, Map<String, Object> data) {
        try {
            Message.Builder messageBuilder = Message.builder().setToken(fcmToken);
            applyContent(messageBuilder::setNotification, messageBuilder::setAndroidConfig,
                    messageBuilder::setApnsConfig, title, body, data);

            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(convertToStringMap(data));
            }

            String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
            log.info("Successfully sent FCM message: {}", response);
        } catch (FirebaseMessagingException e) {
            log.error("Error sending FCM message: {}", e.getMessage(), e);
            if (isInvalidToken(e)) {
                userDeviceMapper.deleteByToken(fcmToken);
                log.info("Deleted invalid FCM token");
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

    private boolean isInvalidToken(FirebaseMessagingException e) {
        MessagingErrorCode code = e.getMessagingErrorCode();
        return code == MessagingErrorCode.UNREGISTERED
                || code == MessagingErrorCode.INVALID_ARGUMENT;
    }

    private Map<String, String> convertToStringMap(Map<String, Object> data) {
        return data.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> String.valueOf(e.getValue())
                ));
    }
}
