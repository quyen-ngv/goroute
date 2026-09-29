package com.ds.goroute.service;

import com.ds.goroute.dto.response.NotificationResponse;
import com.ds.goroute.service.notification.event.TripEvent;
import com.ds.goroute.type.NotificationType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NotificationService {

    void createNotification(UUID userId, UUID tripId, NotificationType type,
                           String title, String body, Map<String, Object> data, UUID actorId);

    void createNotification(UUID userId, TripEvent event);

    boolean sendPushNotification(UUID userId, NotificationType type, Map<String, Object> data);

    List<NotificationResponse> getNotifications(UUID userId, Integer page, Integer size, Boolean unreadOnly, UUID tripId);

    void markAsRead(UUID userId, UUID notificationId);

    /** Marks every unread notification read, or only one trip's when {@code tripId} is given. */
    void markAllAsRead(UUID userId, UUID tripId);

    /**
     * Rewrites a row that stands for a stream of events (a chat thread, a run of likes) with the
     * newest one, and pushes it again when {@code isPushed}.
     *
     * @param data the row's complete new data
     */
    void refreshCoalescedNotification(com.ds.goroute.entity.Notification notification,
                                      Map<String, Object> data, boolean isPushed);

    /** Reading a conversation reads what its chat notifications announced. */
    void markConversationNotificationsRead(UUID userId, UUID conversationId);

    void deleteNotification(UUID userId, UUID notificationId);

    Integer getUnreadCount(UUID userId);

    /**
     * Send admin push notification to multiple users by emails
     * @param emails List of recipient emails
     * @param title Notification title
     * @param body Notification body
     * @param deepLink Optional deep link for redirect
     * @param data Optional custom data
     * @param imageUrl Optional image URL
     * @param priority Priority (high/normal)
     * @return Response with success/failure counts
     */
    com.ds.goroute.dto.response.AdminPushNotificationResponse sendAdminPushNotification(
            List<String> emails,
            String title,
            String body,
            String deepLink,
            Map<String, Object> data,
            String imageUrl,
            String priority
    );

    /**
     * Announcement fan-out for a resolved audience. Unlike the e-mail variants this always writes
     * the in-app row — an account with no registered device still sees the announcement on its
     * next visit — and reports push delivery separately.
     */
    com.ds.goroute.dto.response.AdminPushNotificationResponse sendAdminPushNotificationToUsers(
            List<UUID> userIds,
            String title,
            String body,
            String deepLink,
            Map<String, Object> data,
            String imageUrl,
            String priority
    );

    com.ds.goroute.dto.response.AdminPushNotificationResponse sendAdminPushNotificationToUser(
            UUID userId,
            String email,
            String title,
            String body,
            String deepLink,
            Map<String, Object> data,
            String imageUrl,
            String priority
    );

}
