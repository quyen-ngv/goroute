package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.Notification;
import com.ds.goroute.mapper.NotificationMapper;
import com.ds.goroute.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {
    
    private final NotificationMapper notificationMapper;
    
    @Override
    public void insert(Notification notification) {
        notificationMapper.insert(notification);
    }
    
    @Override
    public Optional<Notification> findById(UUID id) {
        return Optional.ofNullable(notificationMapper.selectById(id));
    }
    
    @Override
    public List<Notification> findByUserId(UUID userId) {
        return notificationMapper.selectByUserId(userId);
    }
    
    @Override
    public List<Notification> findByUserId(UUID userId, UUID tripId) {
        if (tripId == null) {
            return findByUserId(userId);
        }
        return notificationMapper.selectByUserIdAndTripId(userId, tripId);
    }
    
    @Override
    public List<Notification> findUnreadByUserId(UUID userId) {
        return notificationMapper.selectUnreadByUserId(userId);
    }
    
    @Override
    public List<Notification> findUnreadByUserId(UUID userId, UUID tripId) {
        if (tripId == null) {
            return findUnreadByUserId(userId);
        }
        return notificationMapper.selectUnreadByUserIdAndTripId(userId, tripId);
    }

    @Override
    public List<Notification> findPageByUserId(
            UUID userId, UUID tripId, boolean unreadOnly, int limit, int offset) {
        return notificationMapper.findByUserId(userId, tripId, unreadOnly, limit, offset);
    }

    @Override
    public int countUnread(UUID userId) {
        return notificationMapper.countUnread(userId);
    }

    @Override
    public Optional<Notification> findRecentUnreadSocialNotification(
            UUID userId, com.ds.goroute.type.NotificationType type, String targetType, UUID targetId) {
        return Optional.ofNullable(notificationMapper.findRecentUnreadSocialNotification(
                userId, type, targetType, targetId));
    }

    @Override
    public Optional<Notification> findLatestConversationNotification(UUID userId, UUID conversationId) {
        return Optional.ofNullable(notificationMapper.findLatestConversationNotification(userId, conversationId));
    }

    @Override
    public void updateSocialNotification(Notification notification) {
        notificationMapper.updateSocialNotification(notification);
    }
    
    @Override
    public void updateById(Notification notification) {
        notificationMapper.updateById(notification);
    }

    @Override
    public int deleteByIdAndUserId(UUID id, UUID userId) {
        return notificationMapper.deleteByIdAndUserId(id, userId);
    }

    @Override
    public int markAsRead(UUID id, UUID userId) {
        return notificationMapper.markAsRead(id, userId);
    }

    @Override
    public int markAllAsRead(UUID userId, UUID tripId) {
        return notificationMapper.markAllAsRead(userId, tripId);
    }

    @Override
    public int markConversationNotificationsRead(UUID userId, UUID conversationId) {
        return notificationMapper.markConversationNotificationsRead(userId, conversationId);
    }

    @Override
    public void lockTarget(UUID userId, com.ds.goroute.type.NotificationType type, String targetType, UUID targetId) {
        notificationMapper.lockTarget(userId, type, targetType, targetId);
    }

    @Override
    public List<UUID> insertFollowerNotifications(List<UUID> recipientIds, com.ds.goroute.type.NotificationType type,
                                                  String data, UUID actorId, String dedupeKey) {
        if (recipientIds == null || recipientIds.isEmpty()) {
            return List.of();
        }
        return notificationMapper.insertFollowerNotifications(recipientIds, type.name(), data, actorId, dedupeKey);
    }

    @Override
    public List<UUID> findOverFollowingDailyCap(List<UUID> userIds, int hours, int cap) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return notificationMapper.findOverFollowingDailyCap(userIds, hours, cap);
    }

    @Override
    public List<Notification> findUnreadFollowingPosts(UUID userId, int hours, int limit) {
        return notificationMapper.findUnreadFollowingPosts(userId, hours, limit);
    }

    @Override
    public int markFollowingPostsRead(UUID userId, java.util.Collection<String> dedupeKeys) {
        if (dedupeKeys == null || dedupeKeys.isEmpty()) {
            return 0;
        }
        return notificationMapper.markFollowingPostsRead(userId, dedupeKeys);
    }
}
