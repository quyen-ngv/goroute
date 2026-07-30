package com.ds.goroute.repository;

import com.ds.goroute.entity.AppConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppConfigRepository {
    int insert(AppConfig config);
    int update(AppConfig config);
    int delete(UUID id);
    Optional<AppConfig> findById(UUID id);
    Optional<AppConfig> findActiveByLabelAndKey(String label, String key);
    List<AppConfig> findAdmin(String query, String label, Boolean active, int limit, int offset);
}
