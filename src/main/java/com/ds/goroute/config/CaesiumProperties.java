package com.ds.goroute.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * The caesium image compression service (workspace folder {@code caesium_service}), which
 * replaced imgpress. Every image the backend compresses is re-encoded there as WebP.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "caesium")
public class CaesiumProperties {

    @NotBlank
    private String baseUrl = "http://caesium:8080";

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** One image, including the wait for a free slot in the service (its SLOT_WAIT_SECONDS). */
    @NotNull
    private Duration readTimeout = Duration.ofSeconds(90);

    /** WebP quality used by place import and as the console default; 75 keeps photos clean at roughly a third of a JPEG. */
    @Min(30)
    @Max(95)
    private int quality = 75;

    /** Longest side in pixels; larger images are scaled down, smaller ones are never scaled up. */
    @Min(640)
    @Max(4096)
    private int maxLongEdge = 1600;
}
