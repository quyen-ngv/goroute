package com.ds.goroute.thirdparty.goong;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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

    private final AtomicInteger nextKeyIndex = new AtomicInteger();
    private final Map<String, Long> keyCooldownUntilMillis = new ConcurrentHashMap<>();

    @Value("${goong.api-keys:${goong.api-key:}}")
    private String apiKeys;

    @Value("${goong.base-url:https://rsapi.goong.io}")
    private String baseUrl;

    @Value("${goong.max-failover-attempts:2}")
    private int maxFailoverAttempts;

    @Value("${goong.key-cooldown-ms:300000}")
    private long keyCooldownMillis;

    /**
     * Forward a GET request to a Goong endpoint, injecting the server-side key.
     *
     * @param path   Goong endpoint path, e.g. {@code /Place/AutoComplete}
     * @param params whitelisted business query params (no api_key)
     * @return Goong's raw JSON body with the upstream HTTP status preserved
     */
    public ResponseEntity<String> forward(String path, MultiValueMap<String, String> params) {
        List<String> keys = configuredKeys();
        if (keys.isEmpty()) {
            log.warn("GOONG_API_KEY is not configured; Goong proxy is disabled");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":{\"code\":\"CONFIG\",\"message\":\"Goong API key not configured\"}}");
        }

        int attempts = Math.max(1, Math.min(keys.size(), maxFailoverAttempts));
        ResponseEntity<String> lastResponse = null;

        for (int attempt = 0; attempt < attempts; attempt++) {
            String key = selectKey(keys);
            URI uri = buildUri(path, params, key);

            try {
                ResponseEntity<String> response = restClientBuilder.build()
                        .get()
                        .uri(uri)
                        .retrieve()
                        // Passthrough every upstream status (incl. 4xx/5xx) without throwing.
                        .onStatus(status -> true, (request, upstreamResponse) -> { })
                        .toEntity(String.class);

                lastResponse = response;
                if (!shouldFailOver(response.getStatusCode()) || attempt == attempts - 1) {
                    return responseWithoutUpstreamHeaders(response);
                }

                coolDown(key);
                advanceKey();
                log.warn("Goong request to {} returned {}; failing over to another key", path, response.getStatusCode());
            } catch (Exception e) {
                log.warn("Goong request to {} failed: {}", path, e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"UPSTREAM\",\"message\":\"Goong request failed\"}}");
            }
        }

        return lastResponse != null ? responseWithoutUpstreamHeaders(lastResponse) : ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":\"UPSTREAM\",\"message\":\"Goong request failed\"}}");
    }

    private ResponseEntity<String> responseWithoutUpstreamHeaders(ResponseEntity<String> response) {
        String body = response.getBody();
        MediaType contentType = looksLikeJson(body) ? MediaType.APPLICATION_JSON : MediaType.TEXT_PLAIN;
        return ResponseEntity.status(response.getStatusCode())
                .contentType(contentType)
                .body(body);
    }

    private boolean looksLikeJson(String body) {
        if (body == null) {
            return true;
        }
        String trimmed = body.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private List<String> configuredKeys() {
        if (apiKeys == null || apiKeys.isBlank()) {
            return List.of();
        }

        return Arrays.stream(apiKeys.split(","))
                .map(String::trim)
                .filter(key -> !key.isBlank())
                .distinct()
                .toList();
    }

    private String selectKey(List<String> keys) {
        long now = System.currentTimeMillis();
        int start = Math.floorMod(nextKeyIndex.get(), keys.size());

        for (int offset = 0; offset < keys.size(); offset++) {
            String key = keys.get((start + offset) % keys.size());
            Long cooldownUntil = keyCooldownUntilMillis.get(key);
            if (cooldownUntil == null || cooldownUntil <= now) {
                nextKeyIndex.set((start + offset) % keys.size());
                return key;
            }
        }

        keyCooldownUntilMillis.clear();
        return keys.get(start);
    }

    private URI buildUri(String path, MultiValueMap<String, String> params, String apiKey) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path(path)
                .queryParams(params)
                .queryParam("api_key", apiKey)
                .build()
                .encode()
                .toUri();
    }

    private boolean shouldFailOver(HttpStatusCode statusCode) {
        return statusCode.value() == HttpStatus.UNAUTHORIZED.value()
                || statusCode.value() == HttpStatus.FORBIDDEN.value()
                || statusCode.value() == HttpStatus.TOO_MANY_REQUESTS.value();
    }

    private void coolDown(String key) {
        if (keyCooldownMillis <= 0) {
            return;
        }
        keyCooldownUntilMillis.put(key, System.currentTimeMillis() + keyCooldownMillis);
    }

    private void advanceKey() {
        nextKeyIndex.updateAndGet(current -> current == Integer.MAX_VALUE ? 0 : current + 1);
    }
}
