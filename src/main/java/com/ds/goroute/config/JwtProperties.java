package com.ds.goroute.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "application.security.jwt")
public class JwtProperties {

    @NotBlank
    @Size(min = 43)
    private String secretKey;

    @Positive
    private long expiration = 86_400_000L;
}
