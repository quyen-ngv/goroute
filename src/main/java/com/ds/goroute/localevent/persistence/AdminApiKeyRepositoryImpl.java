package com.ds.goroute.localevent.persistence;

import com.ds.goroute.localevent.domain.AdminApiKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AdminApiKeyRepositoryImpl implements AdminApiKeyRepository {

    private final AdminApiKeyMapper mapper;

    @Override
    public void insert(AdminApiKey key) {
        mapper.insert(key);
    }

    @Override
    public Optional<AdminApiKey> findById(UUID id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public Optional<AdminApiKey> findByHash(String keyHash) {
        return Optional.ofNullable(mapper.findByHash(keyHash));
    }

    @Override
    public List<AdminApiKey> findAll(int limit, int offset) {
        return mapper.findAll(limit, offset);
    }

    @Override
    public long countAll() {
        return mapper.countAll();
    }

    @Override
    public void revoke(UUID id, LocalDateTime revokedAt) {
        mapper.revoke(id, revokedAt);
    }

    @Override
    public void touchLastUsed(UUID id, LocalDateTime usedAt, LocalDateTime staleBefore) {
        mapper.touchLastUsed(id, usedAt, staleBefore);
    }
}
