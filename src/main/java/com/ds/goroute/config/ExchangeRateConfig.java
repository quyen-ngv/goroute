package com.ds.goroute.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class ExchangeRateConfig {

    /**
     * Dedicated RestTemplate for exchange-rate API (no auth interceptors).
     *
     * <p>Timeouts are explicit because the default is none: a rate provider that accepts the
     * connection and then stops answering would otherwise hold the calling thread for ever, and
     * the caller's fallback to the stored rate would never run. The payload is a few kilobytes of
     * JSON, so ten seconds to read is already far past "this provider is down".
     */
    @Bean("exchangeRestTemplate")
    public RestTemplate exchangeRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(requestFactory);
    }
}
