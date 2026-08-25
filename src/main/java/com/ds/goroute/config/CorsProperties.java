package com.ds.goroute.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "goroute.cors")
public class CorsProperties {

    @NotEmpty
    private List<String> allowedOriginPatterns = List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "https://onestudy.id.vn");

    @NotEmpty
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    @NotEmpty
    private List<String> allowedHeaders = List.of("*");

    @NotNull
    private Duration maxAge = Duration.ofHours(1);
}
