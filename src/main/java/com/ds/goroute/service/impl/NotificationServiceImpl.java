package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.NotificationResponse;
import com.ds.goroute.dto.response.UserResponse;
import com.ds.goroute.entity.Notification;
import com.ds.goroute.entity.User;
import com.ds.goroute.repository.NotificationRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.external.FirebaseService;
import com.ds.goroute.type.NotificationType;
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
    // private final RedisTemplate<String, Object> redisTemplate;
    private final Gson gson;

    @Override
    @Transactional
    // @CacheEvict(value = "notifications", key = "#userId + '_unread'")
    public void createNotification(UUID userId, UUID tripId, NotificationType type,
                                   String title, String body, Map<String, Object> data, UUID actorId) {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tripId(tripId)
                .type(type)
                .title(title)
                .body(body)
                .data(data != null ? gson.toJson(data) : null)
                .actorId(actorId)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.insert(notification);
        log.info("Created notification: userId={}, type={}", userId, type);

        // Send push notification async
        try {
            sendPushNotification(userId, title, body, data);
        } catch (Exception e) {
            log.error("Failed to send push notification: {}", e.getMessage(), e);
        }
    }

    @Override
    public void sendPushNotification(UUID userId, String title, String body, Map<String, Object> data) {
        try {
            firebaseService.sendPushToUser(userId, title, body, data);
        } catch (Exception e) {
            log.error("Error sending push notification to user {}: {}", userId, e.getMessage(), e);
        }
    }

    @Override
    public List<NotificationResponse> getNotifications(UUID userId, Integer page, Integer size, Boolean unreadOnly) {
        List<Notification> notifications;
        if (unreadOnly != null && unreadOnly) {
            notifications = notificationRepository.findUnreadByUserId(userId);
        } else {
            notifications = notificationRepository.findByUserId(userId);
        }

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

        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .body(notification.getBody())
                .tripId(notification.getTripId())
                .actor(actor)
                .data(notification.getData())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
