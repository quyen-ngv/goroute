package com.ds.goroute.thirdparty.mapbox;

import com.ds.goroute.config.MapboxProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mapbox REST calls the server makes: Search Box (forward, retrieve, reverse), Geocoding v6
 * forward and Directions. Each method returns Mapbox's JSON as is; turning it into the shape
 * the app reads is the caller's job.
 *
 * <p>Every call here is billed. Callers check the budget and the caches before coming here.
 */
@Component
public class MapboxClient {

    static final String SEARCH_BOX = "/search/searchbox/v1";
    static final String GEOCODING = "/search/geocode/v6";
    static final String DIRECTIONS = "/directions/v5/mapbox";

    private final RestClient restClient;
    private final MapboxProperties properties;

    @Autowired
    public MapboxClient(RestClient.Builder restClientBuilder, MapboxProperties properties) {
        this(restClientBuilder.clone().requestFactory(timeouts(properties)).build(), properties);
    }

    /** For tests: a client bound to a mock server keeps the request factory the test installed. */
    MapboxClient(RestClient restClient, MapboxProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /** Text search with coordinates in every result; billed per request, not per session. */
    public JsonNode forward(String query, Double latitude, Double longitude, String language, int limit) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("q", query);
        params.add("limit", String.valueOf(limit));
        params.add("auto_complete", "true");
        if (latitude != null && longitude != null) {
            params.add("proximity", coordinate(longitude) + "," + coordinate(latitude));
        }
        return get("forward", SEARCH_BOX + "/forward", params, language);
    }

    /**
     * Full record of one Search Box feature, by its {@code mapbox_id}. The ID must already be
     * checked against {@code [A-Za-z0-9._=-]+}; it goes into the path as is.
     */
    public JsonNode retrieve(String mapboxId, String language) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("session_token", UUID.randomUUID().toString());
        return get("retrieve", SEARCH_BOX + "/retrieve/" + mapboxId, params, language);
    }

    /** What stands at a point: named places first, then the street and the address. */
    public JsonNode reverse(double latitude, double longitude, String language, int limit) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("longitude", coordinate(longitude));
        params.add("latitude", coordinate(latitude));
        params.add("limit", String.valueOf(limit));
        return get("reverse", SEARCH_BOX + "/reverse", params, language);
    }

    /**
     * Geocoding v6 forward, for names Search Box does not find -- cities, provinces,
     * localities. Search Box answers "Đà Lạt" with a tile shop in Hà Nội; this endpoint does not.
     */
    public JsonNode geocodeForward(String query, String language, int limit) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("q", query);
        params.add("limit", String.valueOf(limit));
        params.add("types", "place,locality,region,district,address");
        return get("geocode", GEOCODING + "/forward", params, language);
    }

    /**
     * One route through every point in order, with a leg between each consecutive pair. Billed
     * as one request whatever the number of points (2 to 25).
     *
     * @param profile     {@code driving}, {@code walking} or {@code cycling}
     * @param lngLatPairs points as {lng, lat}
     */
    public JsonNode directions(String profile, List<double[]> lngLatPairs) {
        String coordinates = lngLatPairs.stream()
                .map(point -> coordinate(point[0]) + "," + coordinate(point[1]))
                .collect(Collectors.joining(";"));
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("geometries", "polyline6");
        params.add("overview", "false");
        params.add("steps", "true");
        return get("directions", DIRECTIONS + "/" + profile + "/" + coordinates, params, null);
    }

    private JsonNode get(String operation, String path, MultiValueMap<String, String> params, String language) {
        if (!isConfigured()) {
            throw new MapboxException("Mapbox token is not configured", 0);
        }
        if (language != null && !language.isBlank()) {
            params.add("language", language);
        }
        URI uri = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path(path)
                .queryParams(params)
                .queryParam("access_token", properties.getServerAccessToken())
                .build()
                .encode()
                .toUri();
        try {
            JsonNode body = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (body == null) {
                throw new MapboxException("Mapbox " + operation + " answered with an empty body", 200);
            }
            return body;
        } catch (RestClientResponseException exception) {
            throw new MapboxException("Mapbox " + operation + " answered " + exception.getStatusCode().value(),
                    exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new MapboxException("Mapbox " + operation + " failed", exception);
        }
    }


    /** Six decimals is ~10 cm; more only makes every URL, and every cache key, different. */
    private static String coordinate(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static SimpleClientHttpRequestFactory timeouts(MapboxProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return factory;
    }
}
