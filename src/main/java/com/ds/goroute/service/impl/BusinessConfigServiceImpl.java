package com.ds.goroute.service.impl;

import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessConfigServiceImpl implements BusinessConfigService {

    private final BusinessConfigStore store;

    @Override
    public int getInt(BusinessConfigKey configKey) {
        return (int) numeric(configKey, BusinessConfigKey.ValueType.INTEGER, configKey.defaultInt());
    }

    @Override
    public double getDecimal(BusinessConfigKey configKey) {
        return numeric(configKey, BusinessConfigKey.ValueType.DECIMAL, configKey.defaultDecimal());
    }

    @Override
    public boolean getBoolean(BusinessConfigKey configKey) {
        require(configKey, BusinessConfigKey.ValueType.BOOLEAN);
        String raw = rawValue(configKey).orElse(null);
        if (raw == null) {
            if (configKey == BusinessConfigKey.PASSPORT_ENABLED && store.explicitlyInactive(configKey)) {
                return false;
            }
            return configKey.defaultBoolean();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        return fallback(configKey, configKey.defaultBoolean());
    }

    @Override
    public String getText(BusinessConfigKey configKey) {
        require(configKey, BusinessConfigKey.ValueType.TEXT);
        return rawValue(configKey)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElseGet(configKey::defaultValue);
    }

    @Override
    public <E extends Enum<E>> E getEnum(BusinessConfigKey configKey, Class<E> enumType) {
        String value = getText(configKey);
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback(configKey, Enum.valueOf(enumType, configKey.defaultValue()));
        }
    }

    private double numeric(BusinessConfigKey configKey, BusinessConfigKey.ValueType expected, double codeDefault) {
        require(configKey, expected);
        String raw = rawValue(configKey).orElse(null);
        if (raw == null) {
            return codeDefault;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            if (configKey.accepts(value)) {
                return value;
            }
        } catch (RuntimeException ignored) {
            // An invalid administrator value must never change product behaviour.
        }
        return fallback(configKey, codeDefault);
    }

    private Optional<String> rawValue(BusinessConfigKey configKey) {
        return store.rawValue(configKey);
    }

    private void require(BusinessConfigKey configKey, BusinessConfigKey.ValueType expected) {
        if (configKey.valueType() != expected) {
            throw new IllegalArgumentException(
                    "Config " + configKey.name() + " is " + configKey.valueType() + ", not " + expected);
        }
    }

    private <T> T fallback(BusinessConfigKey configKey, T codeDefault) {
        log.warn("Ignoring invalid business config {}.{}; using safe default {}",
                configKey.label(), configKey.key(), codeDefault);
        return codeDefault;
    }
}
