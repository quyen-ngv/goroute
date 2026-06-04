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

    @Bean("customKeyGenerator")
    public KeyGenerator keyGenerator() {
        return new CustomKeyGenerator();
    }
}
