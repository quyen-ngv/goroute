package com.ds.goroute.repository.impl;

import com.ds.goroute.mapper.MapCacheMapper;
import com.ds.goroute.repository.MapResponseCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MapResponseCacheRepositoryImpl implements MapResponseCacheRepository {

    private final MapCacheMapper mapper;

    @Override
    public Optional<String> find(String cacheKey, LocalDateTime now) {
        return Optional.ofNullable(mapper.findResponse(cacheKey, now));
    }

    @Override
    public void save(String cacheKey, String operation, String provider, String body, LocalDateTime expiresAt) {
        mapper.upsertResponse(cacheKey, operation, provider, body, expiresAt);
    }
}
