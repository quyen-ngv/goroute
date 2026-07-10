package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.NotificationResponse;
import com.ds.goroute.dto.response.UserResponse;
import com.ds.goroute.entity.Notification;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
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
    private final com.ds.goroute.mapper.UserDeviceMapper userDeviceMapper;
    // private final RedisTemplate<String, Object> redisTemplate;
    private final Gson gson;

    @Override
    @Transactional
    // @CacheEvict(value = "notifications", key = "#userId + '_unread'")
    public void createNotification(UUID userId, UUID tripId, NotificationType type,
                                   String title, String body, Map<String, Object> data, UUID actorId) {
        createNotificationInternal(userId, tripId, type, title, body, data, actorId);
    }

    @Override
    @Transactional
    public void createNotification(UUID userId, TripEvent event) {
        Map<String, Object> data = payloadFactory.build(event);
        createNotificationInternal(userId, event.getTripId(), event.getType(), null, null, data, event.getActorId());
    }

    private boolean createNotificationInternal(UUID userId, UUID tripId, NotificationType type,
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

        // Send push notification and report whether at least one device accepted it.
        try {
            return sendPushNotification(userId, type, data);
        } catch (Exception e) {
            log.error("Failed to send push notification: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean sendPushNotification(UUID userId, NotificationType type, Map<String, Object> data) {
        try {
            return firebaseService.sendPushToUser(userId, type, data);
        } catch (Exception e) {
            log.error("Error sending push notification to user {}: {}", userId, e.getMessage(), e);
            return false;
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

    @Override
    public com.ds.goroute.dto.response.AdminPushNotificationResponse sendAdminPushNotification(
            List<String> emails,
            String title,
            String body,
            String deepLink,
            Map<String, Object> data,
            String imageUrl,
            String priority) {

        List<String> notFoundEmails = new java.util.ArrayList<>();
        List<String> noDeviceEmails = new java.util.ArrayList<>();
        List<String> failedEmails = new java.util.ArrayList<>();
        int successCount = 0;

        for (String email : emails) {
            try {
                // Find user by email
                var userOpt = userRepository.findByEmail(email);
                if (userOpt.isEmpty()) {
                    notFoundEmails.add(email);
                    continue;
                }

                UUID userId = userOpt.get().getId();

                // Check if user has active devices
                java.util.List<com.ds.goroute.entity.UserDevice> devices = 
                    userDeviceMapper.findActiveByUserId(userId);
                
                if (devices.isEmpty()) {
                    noDeviceEmails.add(email);
                    continue;
                }

                Map<String, Object> notificationData = buildAdminNotificationData(
                        title,
                        body,
                        deepLink,
                        data,
                        imageUrl
                );

                // Create notification record in DB
                boolean sent = createNotificationInternal(
                    userId,
                    null, // no tripId for admin notifications
                    NotificationType.ADMIN_ANNOUNCEMENT,
                    title,
                    body,
                    notificationData,
                    null // no actor for admin notifications
                );

                if (sent) {
                    successCount++;
                    log.info("Successfully sent admin push to: {}", email);
                } else {
                    failedEmails.add(email);
                    log.warn("Created admin notification but failed to send push to: {}", email);
                }

            } catch (Exception e) {
                log.error("Failed to send admin push to {}: {}", email, e.getMessage());
                failedEmails.add(email);
            }
        }

        String message = String.format(
            "Sent %d/%d notifications successfully, failed=%d",
            successCount,
            emails.size(),
            failedEmails.size()
        );

        return com.ds.goroute.dto.response.AdminPushNotificationResponse.builder()
                .totalRequested(emails.size())
                .successCount(successCount)
                .notFoundCount(notFoundEmails.size())
                .notFoundEmails(notFoundEmails)
                .noDeviceCount(noDeviceEmails.size())
                .noDeviceEmails(noDeviceEmails)
                .failedCount(failedEmails.size())
                .failedEmails(failedEmails)
                .message(message)
                .build();
    }

    @Override
    public com.ds.goroute.dto.response.AdminPushNotificationResponse sendAdminPushNotificationToUser(
            UUID userId,
            String email,
            String title,
            String body,
            String deepLink,
            Map<String, Object> data,
            String imageUrl,
            String priority) {

        if (userId == null && (email == null || email.isBlank())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Either userId or email is required");
        }

        if (email != null && !email.isBlank()) {
            return sendAdminPushNotification(
                    List.of(email),
                    title,
                    body,
                    deepLink,
                    data,
                    imageUrl,
                    priority
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.USER_NOT_FOUND, "User not found"));

        return sendAdminPushNotification(
                List.of(user.getEmail()),
                title,
                body,
                deepLink,
                data,
                imageUrl,
                priority
        );
    }

    private Map<String, Object> buildAdminNotificationData(String title,
                                                           String body,
                                                           String deepLink,
                                                           Map<String, Object> data,
                                                           String imageUrl) {
        Map<String, Object> notificationData = new java.util.HashMap<>();
        if (data != null) {
            notificationData.putAll(data);
        }
        if (deepLink != null) {
            notificationData.put("deepLink", deepLink);
        }
        if (imageUrl != null) {
            notificationData.put("imageUrl", imageUrl);
        }
        notificationData.put("title", title);
        notificationData.put("body", body);
        return notificationData;
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
                .title(notification.getTitle())
                .body(notification.getBody())
                .deepLink(deepLink)
                .actor(actor)
                .data(data)
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
