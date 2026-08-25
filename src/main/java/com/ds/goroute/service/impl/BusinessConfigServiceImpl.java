package com.ds.goroute.service.impl;

import com.ds.goroute.entity.AppConfig;
import com.ds.goroute.repository.AppConfigRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessConfigServiceImpl implements BusinessConfigService {

    private final AppConfigRepository repository;

    @Override
    @Cacheable(cacheNames = "businessConfig", key = "#configKey.name()", sync = true)
    public int getInt(BusinessConfigKey configKey) {
        return repository.findActiveByLabelAndKey(configKey.label(), configKey.key())
                .map(AppConfig::getValue)
                .map(value -> parseInt(configKey, value))
                .orElse(configKey.defaultValue());
    }

    private int parseInt(BusinessConfigKey configKey, String rawValue) {
        try {
            int value = Integer.parseInt(rawValue.trim());
            if (configKey.accepts(value)) {
                return value;
            }
        } catch (RuntimeException ignored) {
            // Invalid administrator-provided values fall back to the safe code default.
        }
        log.warn(
                "Ignoring invalid business config {}.{} value; using safe default {}",
                configKey.label(),
                configKey.key(),
                configKey.defaultValue());
        return configKey.defaultValue();
    }
}
