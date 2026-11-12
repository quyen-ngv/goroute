package com.ds.goroute.service;

import com.ds.goroute.dto.response.GeoProvinceResponse;
import com.ds.goroute.mapper.GeoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Province names by code, for labelling a place with the province it is in. The 34 active
 * provinces are read once and again after an hour; a failed read keeps the names it had.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProvinceNameResolver {

    private static final long REFRESH_NANOS = Duration.ofHours(1).toNanos();

    private final GeoMapper geoMapper;

    private volatile Map<String, String> namesByCode = Map.of();
    private volatile long loadedAt;
    private volatile boolean loaded;

    /** The province's name, or null for a null or unknown code. */
    public String nameOf(String provinceCode) {
        if (provinceCode == null || provinceCode.isBlank()) {
            return null;
        }
        return names().get(provinceCode);
    }

    private Map<String, String> names() {
        if (!loaded || System.nanoTime() - loadedAt > REFRESH_NANOS) {
            reload();
        }
        return namesByCode;
    }

    private synchronized void reload() {
        if (loaded && System.nanoTime() - loadedAt <= REFRESH_NANOS) {
            return;
        }
        try {
            Map<String, String> names = new HashMap<>();
            for (GeoProvinceResponse province : geoMapper.findProvinces()) {
                if (province.getCode() != null && province.getName() != null) {
                    names.put(province.getCode(), province.getName());
                }
            }
            namesByCode = Map.copyOf(names);
        } catch (RuntimeException e) {
            log.warn("Province names could not be read; keeping {} known", namesByCode.size(), e);
        }
        loadedAt = System.nanoTime();
        loaded = true;
    }
}
