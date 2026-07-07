package com.ds.goroute.thirdparty.goong;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Server-side proxy client for Goong Maps APIs.
 *
 * <p>Keeps the Goong API key on the server: callers pass only the business
 * query params, this client injects {@code api_key} and forwards the request to
 * Goong, returning Goong's raw JSON body and upstream status unchanged so the
 * mobile client can keep parsing the native Goong response shape.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoongClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${goong.api-key:}")
    private String apiKey;

    @Value("${goong.base-url:https://rsapi.goong.io}")
    private String baseUrl;

    /**
     * Forward a GET request to a Goong endpoint, injecting the server-side key.
     *
     * @param path   Goong endpoint path, e.g. {@code /Place/AutoComplete}
     * @param params whitelisted business query params (no api_key)
     * @return Goong's raw JSON body with the upstream HTTP status preserved
     */
    public ResponseEntity<String> forward(String path, MultiValueMap<String, String> params) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GOONG_API_KEY is not configured; Goong proxy is disabled");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":{\"code\":\"CONFIG\",\"message\":\"Goong API key not configured\"}}");
        }

        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path(path)
                .queryParams(params)
                .queryParam("api_key", apiKey)
                .build()
                .encode()
                .toUri();

        try {
            return restClientBuilder.build()
                    .get()
                    .uri(uri)
                    .retrieve()
                    // Passthrough every upstream status (incl. 4xx/5xx) without throwing.
                    .onStatus(status -> true, (request, response) -> { })
                    .toEntity(String.class);
        } catch (Exception e) {
            log.warn("Goong request to {} failed: {}", path, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":{\"code\":\"UPSTREAM\",\"message\":\"Goong request failed\"}}");
        }
    }
}
