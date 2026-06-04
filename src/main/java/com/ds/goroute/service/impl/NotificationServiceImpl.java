package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.NotificationResponse;
import com.ds.goroute.dto.response.UserResponse;
import com.ds.goroute.entity.Notification;
import com.ds.goroute.entity.User;
import com.ds.goroute.repository.NotificationRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.external.FirebaseService;
import com.ds.goroute.service.notification.NotificationPayloadFactory;
import com.ds.goroute.service.notification.event.TripEvent;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.utils.JsonUtils;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// import org.springframework.cache.annotation.CacheEvict;
// import org.springframework.cache.annotation.Cacheable;
// import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
// import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FirebaseService firebaseService;
    private final NotificationPayloadFactory payloadFactory;
    // private final RedisTemplate<String, Object> redisTemplate;
    private final Gson gson;

    @Override
    @Transactional
    // @CacheEvict(value = "notifications", key = "#userId + '_unread'")
    public void createNotification(UUID userId, UUID tripId, NotificationType type,
                                   String title, String body, Map<String, Object> data, UUID actorId) {
        createNotification(userId, tripId, type, data, actorId);
    }

    @Override
    @Transactional
    public void createNotification(UUID userId, TripEvent event) {
        Map<String, Object> data = payloadFactory.build(event);
        createNotification(userId, event.getTripId(), event.getType(), data, event.getActorId());
    }

    private void createNotification(UUID userId, UUID tripId, NotificationType type,
                                    Map<String, Object> data, UUID actorId) {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tripId(tripId)
                .type(type)
                .title(null)
                .body(null)
                .data(data != null ? gson.toJson(data) : null)
                .actorId(actorId)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.insert(notification);
        log.info("Created notification: userId={}, type={}", userId, type);

        // Send push notification async
        try {
            sendPushNotification(userId, type, data);
        } catch (Exception e) {
            log.error("Failed to send push notification: {}", e.getMessage(), e);
        }
    }

    @Override
    public void sendPushNotification(UUID userId, NotificationType type, Map<String, Object> data) {
        try {
            firebaseService.sendPushToUser(userId, type, data);
        } catch (Exception e) {
            log.error("Error sending push notification to user {}: {}", userId, e.getMessage(), e);
        }
    }

    @Override
    public List<NotificationResponse> getNotifications(UUID userId, Integer page, Integer size, Boolean unreadOnly, UUID tripId) {
        log.info("Service getNotifications - userId: {}, tripId: {}, unreadOnly: {}", userId, tripId, unreadOnly);
        List<Notification> notifications;
        if (unreadOnly != null && unreadOnly) {
            notifications = notificationRepository.findUnreadByUserId(userId, tripId);
        } else {
            notifications = notificationRepository.findByUserId(userId, tripId);
        }
        log.info("Service getNotifications - found {} notifications from DB", notifications.size());

        return notifications.stream()
                .skip((long) page * size)
                .limit(size)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    // @CacheEvict(value = "notifications", key = "#notificationId")
    public void markAsRead(UUID notificationId) {
        notificationRepository.markAsRead(notificationId);

        // Invalidate unread count cache
        // Notification notification = notificationRepository.findById(notificationId);
        // if (notification != null) {
        //     String cacheKey = "notifications:" + notification.getUserId() + "_unread";
        //     redisTemplate.delete(cacheKey);
        // }
    }

    @Override
    @Transactional
    // @CacheEvict(value = "notifications", key = "#userId + '_unread'")
    public void markAllAsRead(UUID userId) {
        notificationRepository.findByUserId(userId).forEach(n -> {
            n.setIsRead(true);
            notificationRepository.updateById(n);
        });
    }

    @Override
    @Transactional
    // @CacheEvict(value = "notifications", allEntries = true)
    public void deleteNotification(UUID notificationId) {
        notificationRepository.deleteById(notificationId);
    }

    @Override
    // @Cacheable(value = "notifications", key = "#userId + '_unread'")
    public Integer getUnreadCount(UUID userId) {
        return (int) notificationRepository.findUnreadByUserId(userId).size();
    }

    private NotificationResponse toResponse(Notification notification) {
        UserResponse actor = null;
        if (notification.getActorId() != null) {
            User actorUser = userRepository.findById(notification.getActorId()).orElse(null);
            if (actorUser != null) {
                actor = UserResponse.builder()
                        .id(actorUser.getId())
                        .fullName(actorUser.getFullName())
                        .avatarUrl(actorUser.getAvatarUrl())
                        .build();
            }
        }

        // Parse deepLink from data JSON
        String deepLink = null;
        if (notification.getData() != null) {
            try {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> dataMap = gson.fromJson(notification.getData(), java.util.Map.class);
                if (dataMap != null && dataMap.containsKey("deepLink")) {
                    deepLink = (String) dataMap.get("deepLink");
                }
            } catch (Exception e) {
                log.warn("Failed to parse deepLink from notification data: {}", e.getMessage());
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = notification.getData() != null
                ? JsonUtils.fromJson(notification.getData(), Map.class)
                : null;

        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .tripId(notification.getTripId())
                .deepLink(deepLink)
                .actor(actor)
                .data(data)
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
