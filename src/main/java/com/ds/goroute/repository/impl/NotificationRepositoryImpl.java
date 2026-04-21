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
    public void updateById(Notification notification) {
        notificationMapper.updateById(notification);
    }
    
    @Override
    public void deleteById(UUID id) {
        notificationMapper.deleteById(id);
    }
    
    @Override
    public void markAsRead(UUID id) {
        notificationMapper.markAsRead(id);
    }
}
