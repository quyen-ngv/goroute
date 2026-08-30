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

    int markAllAsRead(UUID userId);
}
