package com.ds.goroute.repository;

import com.ds.goroute.entity.Notification;
import com.ds.goroute.type.NotificationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {
    void insert(Notification notification);
    
    Optional<Notification> findById(UUID id);
    
    List<Notification> findByUserId(UUID userId);
    
    List<Notification> findByUserId(UUID userId, UUID tripId);
    
    List<Notification> findUnreadByUserId(UUID userId);
    
    List<Notification> findUnreadByUserId(UUID userId, UUID tripId);

    List<Notification> findPageByUserId(UUID userId, UUID tripId, boolean unreadOnly, int limit, int offset);

    int countUnread(UUID userId);

    Optional<Notification> findRecentUnreadSocialNotification(
            UUID userId, NotificationType type, String targetType, UUID targetId);

    void updateSocialNotification(Notification notification);

    void updateById(Notification notification);

    int deleteByIdAndUserId(UUID id, UUID userId);

    int markAsRead(UUID id, UUID userId);

    /** Marks the person's unread notifications read; only one trip's when {@code tripId} is given. */
    int markAllAsRead(UUID userId, UUID tripId);

    /** Marks the person's unread chat notifications of one conversation read. */
    int markConversationNotificationsRead(UUID userId, UUID conversationId);

    /**
     * Serialises "find the unread row for this target, else insert one" for one recipient and
     * target until the current transaction ends, so two concurrent likes or messages cannot both
     * miss the row and insert two. Must be called inside a transaction.
     */
    void lockTarget(UUID userId, NotificationType type, String targetType, UUID targetId);
}
