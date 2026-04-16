package com.ds.goroute.service.impl;

import com.ds.goroute.service.EditLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class EditLockServiceImpl implements EditLockService {
    
    private static final long LOCK_TIMEOUT_SECONDS = 60;
    
    // In-memory lock storage (replace Redis)
    private final Map<UUID, LockInfo> locks = new ConcurrentHashMap<>();
    
    private static class LockInfo {
        UUID userId;
        long expiryTime;
        
        LockInfo(UUID userId, long expiryTime) {
            this.userId = userId;
            this.expiryTime = expiryTime;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    @Override
    public boolean acquireLock(UUID activityId, UUID userId) {
        cleanExpiredLocks();
        
        LockInfo existingLock = locks.get(activityId);
        
        // No lock exists or lock expired
        if (existingLock == null || existingLock.isExpired()) {
            locks.put(activityId, new LockInfo(userId, 
                System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(LOCK_TIMEOUT_SECONDS)));
            log.info("Lock acquired: activity={}, user={}", activityId, userId);
            return true;
        }
        
        // Current user already owns the lock
        if (userId.equals(existingLock.userId)) {
            extendLock(activityId, userId);
            return true;
        }
        
        log.warn("Lock acquisition failed: activity={}, user={}, owner={}", 
                activityId, userId, existingLock.userId);
        return false;
    }

    @Override
    public void releaseLock(UUID activityId, UUID userId) {
        LockInfo lock = locks.get(activityId);
        
        if (lock != null && userId.equals(lock.userId)) {
            locks.remove(activityId);
            log.info("Lock released: activity={}, user={}", activityId, userId);
        }
    }

    @Override
    public void extendLock(UUID activityId, UUID userId) {
        LockInfo lock = locks.get(activityId);
        
        if (lock != null && userId.equals(lock.userId)) {
            lock.expiryTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(LOCK_TIMEOUT_SECONDS);
            log.debug("Lock extended: activity={}, user={}", activityId, userId);
        }
    }

    @Override
    public UUID getLockOwner(UUID activityId) {
        cleanExpiredLocks();
        LockInfo lock = locks.get(activityId);
        return (lock != null && !lock.isExpired()) ? lock.userId : null;
    }
    
    private void cleanExpiredLocks() {
        locks.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
