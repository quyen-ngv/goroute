package com.ds.goroute.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Google Places API (New). An empty {@code apiKey} switches the integration off: the client
 * reports itself unconfigured and nothing is sent to Google.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "google.places")
public class GooglePlacesProperties {

    private String apiKey = "";

    @NotBlank
    private String baseUrl = "https://places.googleapis.com";
}
