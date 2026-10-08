package com.ds.goroute.config.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@EnableCaching
@EnableConfigurationProperties(LocalCacheProperties.class)
@ConditionalOnProperty(value = "app.cache.local.enable", havingValue = "true", matchIfMissing = true)
public class LocalCacheConfig {

    @Bean
    public Caffeine<Object, Object> caffeineConfig(LocalCacheProperties properties) {
        log.info("Configuring Caffeine cache with timeout: {} seconds", properties.getTimeoutSeconds());
        return Caffeine.newBuilder()
                .expireAfterWrite(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
    }

    @Bean
    @Primary
    public CacheManager localCacheManager(Caffeine<Object, Object> caffeineCacheBuilder) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        log.info("Configuring local cache manager");
        log.info("Using caffeine: {}", caffeineCacheBuilder.toString());
        cacheManager.setCaffeine(caffeineCacheBuilder);
        return cacheManager;
    }

    /** Short-lived caches for the expensive, read-only catalogue searches. */
    @Bean("searchCacheManager")
    public CacheManager searchCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "activityBookingSearch",
                "activityBookingByPlaceSearch");
        cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(2, TimeUnit.MINUTES)
                        .maximumSize(2_000));
        // One entry holds up to 100 places, so a count bound let the cache grow to 200,000
        // place responses. Bounded by the places held instead.
        cacheManager.registerCustomCache("placeSearch",
                Caffeine.newBuilder()
                        .expireAfterWrite(2, TimeUnit.MINUTES)
                        .maximumWeight(PLACE_SEARCH_MAX_CACHED_PLACES)
                        .weigher((Object key, Object value) -> value instanceof java.util.Collection<?> list
                                ? list.size() + 1 : 1)
                        .build());
        return cacheManager;
    }

    /** Place responses the place search cache may hold across all its entries. */
    static final long PLACE_SEARCH_MAX_CACHED_PLACES = 20_000;

    @Bean("placeSearchCacheKeyGenerator")
    public KeyGenerator placeSearchCacheKeyGenerator() {
        // searchPlaces(keyword, latitude, longitude, ...): parameters 1 and 2 are the point.
        return new SearchCacheKeyGenerator(java.util.Set.of(1, 2));
    }

    /** Dedicated 6-hour cache for exchange rates (daily updated, low churn). */
    @Bean("foodsByCityCacheManager")
    public CacheManager foodsByCityCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("foodsByCity");
        cacheManager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS));
        return cacheManager;
    }

    @Bean("exchangeRateCacheManager")
    public CacheManager exchangeRateCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("exchangeRates");
        cacheManager.setCaffeine(
            Caffeine.newBuilder().expireAfterWrite(6, java.util.concurrent.TimeUnit.HOURS)
        );
        return cacheManager;
    }

    /**
     * Weather snapshots, keyed by grid cell rather than by location.
     *
     * <p>30 minutes matches how often Open-Meteo advances its current-conditions slot and
     * keeps the free tier (10k calls/day) comfortable: one city costs at most 96 upstream
     * calls a day no matter how many curated location images point into it.
     */
    @Bean("weatherCacheManager")
    public CacheManager weatherCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("weatherSnapshot");
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(5_000)
        );
        return cacheManager;
    }

    /**
     * Boundary GeoJSON per province (a few hundred KB each) and the province outlines.
     * Long-lived because the shapes change once per decree; evicted by the geo backfill.
     */
    @Bean("geoCacheManager")
    public CacheManager geoCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("geoJson");
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .expireAfterWrite(24, TimeUnit.HOURS)
                .maximumSize(64)
        );
        return cacheManager;
    }

    @Bean("customKeyGenerator")
    public KeyGenerator keyGenerator() {
        return new CustomKeyGenerator();
    }

    @Bean("searchCacheKeyGenerator")
    public KeyGenerator searchCacheKeyGenerator() {
        return new SearchCacheKeyGenerator();
    }
}
