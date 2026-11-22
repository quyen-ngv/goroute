package com.ds.goroute.localevent.persistence;

import com.ds.goroute.localevent.domain.AdminApiKey;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence facade over {@link AdminApiKeyMapper}. */
public interface AdminApiKeyRepository {

    void insert(AdminApiKey key);

    Optional<AdminApiKey> findById(UUID id);

    Optional<AdminApiKey> findByHash(String keyHash);

    List<AdminApiKey> findAll(int limit, int offset);

    long countAll();

    void revoke(UUID id, LocalDateTime revokedAt);

    void touchLastUsed(UUID id, LocalDateTime usedAt, LocalDateTime staleBefore);
}
