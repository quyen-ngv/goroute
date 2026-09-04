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

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirebaseService {

    private final UserDeviceMapper userDeviceMapper;
    private final NotificationTemplateRenderer templateRenderer;

    public boolean sendPushToUser(UUID userId, NotificationType type, Map<String, Object> data) {
        List<UserDevice> devices = userDeviceMapper.findActiveByUserId(userId);

        if (devices.isEmpty()) {
            log.warn("No active devices found for user: {}", userId);
            return false;
        }

        boolean sent = false;
        for (UserDevice device : devices) {
            try {
                NotificationMessage message = templateRenderer.render(type, data, device.getLanguage());
                sendPush(device.getFcmToken(), message.title(), message.body(), data);
                sent = true;
            } catch (Exception e) {
                log.error("Failed to send push to device {}: {}", device.getId(), e.getMessage(), e);
            }
        }
        return sent;
    }

    public void sendPush(String fcmToken, String title, String body, Map<String, Object> data) {
        try {
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

            Message.Builder messageBuilder = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(notificationBuilder.build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(androidNotificationBuilder.build())
                            .build())
                    .setApnsConfig(apnsConfigBuilder.setAps(apsBuilder.build()).build());

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
