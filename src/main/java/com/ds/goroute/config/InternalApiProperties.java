package com.ds.goroute.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "goroute.internal-auth")
public record InternalApiProperties(
        String aiTripToken,
        String scrapeCallbackToken
) {
}
