package com.ds.goroute.service.impl;

import com.ds.goroute.mapper.GeoMapper;
import com.ds.goroute.repository.WardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Cached GeoJSON of the boundary layers the app draws.
 *
 * <p>Its own bean for the same reason as {@code BusinessConfigStore}: {@code @Cacheable}
 * only works through the Spring proxy, so these cannot be private methods of the service.
 * A province's ward layer is a few hundred kilobytes assembled by PostGIS; it changes only
 * when a dataset is loaded, which is why the cache is evicted by the backfill and nothing else.
 */
@Component
@RequiredArgsConstructor
public class GeoJsonStore {

    public static final String CACHE = "geoJson";

    private final WardRepository wardRepository;
    private final GeoMapper geoMapper;

    @Cacheable(cacheNames = CACHE, cacheManager = "geoCacheManager", key = "'wards:' + #provinceCode", sync = true)
    public Optional<String> wardsGeoJson(String provinceCode) {
        return wardRepository.displayGeoJsonByProvince(provinceCode);
    }

    @Cacheable(cacheNames = CACHE, cacheManager = "geoCacheManager", key = "'provinces'", sync = true)
    public Optional<String> provincesGeoJson() {
        return Optional.ofNullable(geoMapper.provincesDisplayGeoJson());
    }

    @CacheEvict(cacheNames = CACHE, cacheManager = "geoCacheManager", allEntries = true)
    public void evictAll() {
        // Cache eviction only.
    }
}
