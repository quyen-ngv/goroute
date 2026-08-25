package com.ds.goroute.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "goroute.api-limits")
public class ApiRequestLimitsProperties {

    @Min(1)
    @Max(1_000_000)
    private int maxPageIndex = 100_000;

    @Min(1)
    @Max(10_000)
    private int maxPageSize = 500;

    @Min(1)
    @Max(100_000)
    private int maxResultLimit = 500;
}
