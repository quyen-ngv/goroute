package com.ds.goroute.service;

import com.ds.goroute.dto.response.NotificationResponse;
import com.ds.goroute.type.NotificationType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NotificationService {
    
    void createNotification(UUID userId, UUID tripId, NotificationType type, 
                           String title, String body, Map<String, Object> data, UUID actorId);
    
    void sendPushNotification(UUID userId, String title, String body, Map<String, Object> data);
    
    List<NotificationResponse> getNotifications(UUID userId, Integer page, Integer size, Boolean unreadOnly);
    
    void markAsRead(UUID notificationId);
    
    void markAllAsRead(UUID userId);
    
    void deleteNotification(UUID notificationId);
    
    Integer getUnreadCount(UUID userId);
}
