package com.ds.goroute.service;

import java.util.UUID;

public interface EditLockService {
    boolean acquireLock(UUID activityId, UUID userId);
    void releaseLock(UUID activityId, UUID userId);
    void extendLock(UUID activityId, UUID userId);
    UUID getLockOwner(UUID activityId);
}
