package com.ds.goroute.service.impl;

import com.ds.goroute.entity.AppConfig;
import com.ds.goroute.repository.AppConfigRepository;
import com.ds.goroute.type.BusinessConfigKey;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Cached raw read of one administrable configuration value.
 *
 * <p>This lives in its own bean on purpose: caching only applies through the Spring
 * proxy, so a {@code @Cacheable} method called from a sibling method of the same class
 * would silently hit the database on every read.
 */
@Component
@RequiredArgsConstructor
public class BusinessConfigStore {

    private final AppConfigRepository repository;

    @Cacheable(cacheNames = "businessConfig", key = "#configKey.name()", sync = true)
    public Optional<String> rawValue(BusinessConfigKey configKey) {
        return repository.findActiveByLabelAndKey(configKey.label(), configKey.key())
                .map(AppConfig::getValue);
    }

    @Cacheable(cacheNames = "businessConfig", key = "#configKey.name() + ':inactive'", sync = true)
    public boolean explicitlyInactive(BusinessConfigKey configKey) {
        return repository.findByLabelAndKey(configKey.label(), configKey.key())
                .map(config -> !Boolean.TRUE.equals(config.getIsActive()))
                .orElse(false);
    }
}
