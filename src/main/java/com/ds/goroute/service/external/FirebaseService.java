package com.ds.goroute.service.external;

import com.ds.goroute.entity.UserDevice;
import com.ds.goroute.mapper.UserDeviceMapper;
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

    public void sendPushToUser(UUID userId, String title, String body, Map<String, Object> data) {
        List<UserDevice> devices = userDeviceMapper.findActiveByUserId(userId);
        
        if (devices.isEmpty()) {
            log.warn("No active devices found for user: {}", userId);
            return;
        }

        for (UserDevice device : devices) {
            try {
                sendPush(device.getFcmToken(), title, body, data);
            } catch (Exception e) {
                log.error("Failed to send push to device {}: {}", device.getId(), e.getMessage());
            }
        }
    }

    public void sendPush(String fcmToken, String title, String body, Map<String, Object> data) {
        try {
            Notification notification = Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build();

            Message.Builder messageBuilder = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(notification);

            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(convertToStringMap(data));
            }

            String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
            log.info("Successfully sent FCM message: {}", response);
        } catch (FirebaseMessagingException e) {
            log.error("Error sending FCM message: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send push notification", e);
        }
    }

    private Map<String, String> convertToStringMap(Map<String, Object> data) {
        return data.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> String.valueOf(e.getValue())
                ));
    }
}
