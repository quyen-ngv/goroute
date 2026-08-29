package com.ds.goroute.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Wiring for the image recognition backend (MOD-04).
 *
 * <p>Only connection details live here. The thresholds that decide what counts as a
 * violation are runtime configuration, because tuning them is a weekly operational task
 * during the first month and must not need a release.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "goroute.moderation.image")
public class ImageModerationProperties {

    /** Endpoint that accepts an image and answers with a score per policy group. */
    private String url;

    private String apiKey;

    private Duration timeout = Duration.ofSeconds(5);

    /**
     * Entry points allowed to run at a relaxed level. Administrator and partner uploads
     * still go through the check; they are simply trusted a little further.
     */
    private double relaxedThresholdBonus = 0.1d;
}
