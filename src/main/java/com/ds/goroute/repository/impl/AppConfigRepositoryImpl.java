package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.AppConfig;
import com.ds.goroute.mapper.AppConfigMapper;
import com.ds.goroute.repository.AppConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AppConfigRepositoryImpl implements AppConfigRepository {
    private final AppConfigMapper mapper;

    @Override public int insert(AppConfig config) { return mapper.insert(config); }
    @Override public int update(AppConfig config) { return mapper.update(config); }
    @Override public int delete(UUID id) { return mapper.delete(id); }
    @Override public Optional<AppConfig> findById(UUID id) { return Optional.ofNullable(mapper.findById(id)); }
    @Override public Optional<AppConfig> findActiveByLabelAndKey(String label, String key) {
        return Optional.ofNullable(mapper.findActiveByLabelAndKey(label, key));
    }
    @Override public List<AppConfig> findAdmin(String query, String label, Boolean active, int limit, int offset) {
        return mapper.findAdmin(query, label, active, limit, offset);
    }
}
