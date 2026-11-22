package com.ds.goroute.service.impl;

import com.ds.goroute.service.ExchangeRateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The 1:1 fallback for a failed lookup is returned but never cached. */
class ExchangeRateServiceImplCacheTest {

    private static final RestTemplate REST = mock(RestTemplate.class);

    private final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(CacheConfig.class);
    private final ExchangeRateService service = context.getBean(ExchangeRateService.class);

    @AfterEach
    void tearDown() {
        context.close();
        reset(REST);
    }

    @Test
    void doesNotCacheTheFallbackWhenUpstreamFails() {
        when(REST.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new ResourceAccessException("down"));

        assertThat(service.getRate("USD", "VND")).isEqualByComparingTo(BigDecimal.ONE);

        when(REST.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"usd\":{\"vnd\":26000}}"));

        assertThat(service.getRate("USD", "VND")).isEqualByComparingTo("26000");
    }

    @Test
    void cachesARealRate() {
        when(REST.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"usd\":{\"vnd\":26000}}"));

        service.getRate("USD", "VND");
        service.getRate("usd", "vnd");

        verify(REST, times(1)).getForEntity(anyString(), eq(String.class));
    }

    @Configuration
    @EnableCaching
    static class CacheConfig {
        @Bean
        CacheManager exchangeRateCacheManager() {
            return new ConcurrentMapCacheManager("exchangeRates");
        }

        @Bean
        ExchangeRateService exchangeRateService() {
            return new ExchangeRateServiceImpl(REST, new ObjectMapper());
        }
    }
}
