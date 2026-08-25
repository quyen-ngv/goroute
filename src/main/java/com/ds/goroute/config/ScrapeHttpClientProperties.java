package com.ds.goroute.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "goroute.http-client.scrape")
public class ScrapeHttpClientProperties {

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(10);

    @NotNull
    private Duration readTimeout = Duration.ofSeconds(30);

    @AssertTrue(message = "scrape connectTimeout must be between 1 second and 5 minutes")
    public boolean isConnectTimeoutValid() {
        return within(connectTimeout, Duration.ofSeconds(1), Duration.ofMinutes(5));
    }

    @AssertTrue(message = "scrape readTimeout must be between 1 second and 10 minutes")
    public boolean isReadTimeoutValid() {
        return within(readTimeout, Duration.ofSeconds(1), Duration.ofMinutes(10));
    }

    private boolean within(Duration value, Duration minimum, Duration maximum) {
        return value != null && value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }
}
