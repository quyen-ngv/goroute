package com.ds.goroute.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ExchangeRateConfig {

    /** Dedicated RestTemplate for exchange-rate API (no auth interceptors). */
    @Bean("exchangeRestTemplate")
    public RestTemplate exchangeRestTemplate() {
        return new RestTemplate();
    }
}
