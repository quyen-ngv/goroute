package com.ds.goroute.service.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedisService {

    // private final RedissonClient redissonClient;
    // private final RedisTemplate<String, Object> redisTemplate;
    // private final RedisTemplate<String, Object> redisTemplateForStringKeyStringValue;
    // @Qualifier("cacheThreadPool")
    // private final Executor executor;
    // private final ObjectMapper objectMapper;
    // private final RedisScript<Long> joinQueueScript;

    // --- Locking Operations ---

    /**
     * Try to acquire a distributed lock using default timeout settings.
     * DISABLED: Redis not available
     */
    public Object acquireLock(String key) {
        log.warn("Redis is disabled - acquireLock returning null");
        return null;
    }

    /**
     * Try to acquire a distributed lock with custom timeout.
     * DISABLED: Redis not available
     */
    public Object acquireLock(String key, Integer waitTime, Integer leaseTime) {
        log.warn("Redis is disabled - acquireLock returning null");
        return null;
    }

    public void unlock(Object lock, String key) {
        log.warn("Redis is disabled - unlock ignored");
    }

    // --- Value Operations ---

    /**
     * Set value with TTL.
     * DISABLED: Redis not available
     */
    public void set(String key, Object value, long ttlInSeconds) {
        log.warn("Redis is disabled - set ignored");
    }

    /**
     * Set string value specifically using the string template.
     * DISABLED: Redis not available
     */
    public void setString(String key, Object value, long ttlInSeconds) {
        log.warn("Redis is disabled - setString ignored");
    }

    /**
     * Get value and cast to target class.
     * DISABLED: Redis not available
     */
    public <T> T get(String key, Class<T> clazz) {
        log.warn("Redis is disabled - get returning null");
        return null;
    }

    // --- Hash Operations ---

    /**
     * Get value from a Hash map.
     * DISABLED: Redis not available
     */
    public <T> T getHash(String key, String hashKey, Class<T> clazz) {
        log.warn("Redis is disabled - getHash returning null");
        return null;
    }

    public void putHash(String key, String hashKey, Object value) {
        log.warn("Redis is disabled - putHash ignored");
    }

    // --- Common Operations ---

    public void delete(String key) {
        log.warn("Redis is disabled - delete ignored");
    }

    public Boolean hasKey(String key) {
        log.warn("Redis is disabled - hasKey returning false");
        return false;
    }

    /**
     * Set value only if key does not exist (Atomic SETNX).
     * DISABLED: Redis not available
     */
    public void setIfAbsent(String key, Object value, long ttlInSeconds) {
        log.warn("Redis is disabled - setIfAbsent ignored");
    }

    /**
     * Set String value only if key does not exist (Atomic SETNX).
     * DISABLED: Redis not available
     */
    public void setStringIfAbsent(String key, Object value, long ttlInSeconds) {
        log.warn("Redis is disabled - setStringIfAbsent ignored");
    }

    /*
    Increase atomic
    DISABLED: Redis not available
     */
    public Long increaseKeyBy(String key, Long value) {
        log.warn("Redis is disabled - increaseKeyBy returning null");
        return null;
    }

    public <T> void setHash(String key, T object, long timeout) {
        log.warn("Redis is disabled - setHash ignored");
    }

    public <T> T getHash(String key, Class<T> clazz) {
        log.warn("Redis is disabled - getHash returning null");
        return null;
    }

    public void updateHashField(String key, String field, Object value) {
        log.warn("Redis is disabled - updateHashField ignored");
    }
}
