package com.ds.goroute.thirdparty.google.places;

import com.ds.goroute.config.GooglePlacesProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Optional;

/**
 * Google Places API (New) over REST.
 *
 * <p>Google bills each request by the most expensive field in its field mask, so every call
 * sends {@link #PLACE_FIELDS} and nothing more. Rating, opening hours, reviews and photos are
 * deliberately absent: asking for any of them moves the request to a dearer SKU.
 *
 * <p>Timeouts come from the application-wide {@code RestClientCustomizer}; this client must not
 * set its own request factory, or a test's mock server is silently bypassed.
 */
@Component
public class GooglePlacesClient {

    static final String API_KEY_HEADER = "X-Goog-Api-Key";
    static final String FIELD_MASK_HEADER = "X-Goog-FieldMask";
    static final String PLACE_FIELDS = "id,displayName,formattedAddress,location,types,primaryType,googleMapsUri";
    static final String SEARCH_FIELDS = "places.id,places.displayName,places.formattedAddress,places.location,"
            + "places.types,places.primaryType,places.googleMapsUri";

    private final GooglePlacesProperties properties;
    private final RestClient restClient;

    public GooglePlacesClient(RestClient.Builder restClientBuilder, GooglePlacesProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.getBaseUrl()).build();
    }

    public boolean isConfigured() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    public List<GooglePlace> searchText(GooglePlacesTextSearchRequest request) {
        String apiKey = requireApiKey();
        try {
            GooglePlacesTextSearchResponse response = restClient.post()
                    .uri("/v1/places:searchText")
                    .header(API_KEY_HEADER, apiKey)
                    .header(FIELD_MASK_HEADER, SEARCH_FIELDS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GooglePlacesTextSearchResponse.class);
            return response == null || response.getPlaces() == null ? List.of() : response.getPlaces();
        } catch (RestClientResponseException e) {
            throw new GooglePlacesException("Google Places text search answered " + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new GooglePlacesException("Google Places text search failed", e);
        }
    }

    /** @return empty when Google no longer knows the place ID */
    public Optional<GooglePlace> getPlace(String placeId) {
        String apiKey = requireApiKey();
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/v1/places/{placeId}", placeId)
                    .header(API_KEY_HEADER, apiKey)
                    .header(FIELD_MASK_HEADER, PLACE_FIELDS)
                    .retrieve()
                    .body(GooglePlace.class));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            throw new GooglePlacesException("Google Places details answered " + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new GooglePlacesException("Google Places details failed", e);
        }
    }

    private String requireApiKey() {
        if (!isConfigured()) {
            throw new GooglePlacesException("Google Places API key is not configured");
        }
        return properties.getApiKey().trim();
    }
}
