package com.ds.goroute.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Mapbox Search Box, Geocoding and Directions, called from the server.
 *
 * <p>An empty {@code serverAccessToken} switches Mapbox off: the provider resolver then
 * answers with Goong, or with the catalogue alone when Goong has no key either. The token
 * is read from {@code MAPBOX_SERVER_ACCESS_TOKEN}, falling back to {@code MAPBOX_ACCESS_TOKEN}
 * -- today one token serves the app and the server, and splitting them later is a change of
 * environment value, not of code.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "mapbox")
public class MapboxProperties {

    private String serverAccessToken = "";

    @NotBlank
    private String baseUrl = "https://api.mapbox.com";

    /** Short on purpose: a search that waits longer than this is better answered by the fallback. */
    private int connectTimeoutMs = 3000;

    private int readTimeoutMs = 8000;

    public boolean isConfigured() {
        return serverAccessToken != null && !serverAccessToken.isBlank();
    }
}
