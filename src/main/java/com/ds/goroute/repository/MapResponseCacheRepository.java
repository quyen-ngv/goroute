package com.ds.goroute.repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Whole answers of a map provider kept for a while ({@code map_response_cache}, V226), keyed by a
 * hash of the normalised request, so the same question is paid for once. Times are UTC, as
 * the other writers of the table store them.
 */
public interface MapResponseCacheRepository {

    /** The stored body for the key, unless it has expired by {@code now}. */
    Optional<String> find(String cacheKey, LocalDateTime now);

    /** Inserts or replaces the body for the key. */
    void save(String cacheKey, String operation, String provider, String body, LocalDateTime expiresAt);
}
