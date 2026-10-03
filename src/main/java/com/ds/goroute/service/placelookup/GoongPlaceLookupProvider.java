package com.ds.goroute.service.placelookup;

import com.ds.goroute.thirdparty.goong.GoongClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Goong as a {@link PlaceLookupProvider}: autocomplete for search, Place Detail for details.
 *
 * <p>Autocomplete returns no coordinates, so a search hit has none; call
 * {@link #getDetails(String)} for them. Goong IDs are not Google place IDs.
 */
@Component
@RequiredArgsConstructor
public class GoongPlaceLookupProvider implements PlaceLookupProvider {

    private static final String AUTOCOMPLETE_PATH = "/Place/AutoComplete";
    private static final String DETAIL_PATH = "/Place/Detail";

    private final GoongClient goongClient;
    private final ObjectMapper objectMapper;

    @Override
    public PlaceLookupProviderType type() {
        return PlaceLookupProviderType.GOONG;
    }

    @Override
    public boolean isAvailable() {
        return goongClient.isConfigured();
    }

    @Override
    public List<PlaceHit> searchText(PlaceLookupQuery query) {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("input", query.text());
        if (query.hasBias()) {
            params.add("location", query.biasLatitude() + "," + query.biasLongitude());
        }
        if (query.biasRadiusMeters() != null) {
            params.add("radius", query.biasRadiusMeters().toString());
        }
        params.add("limit", Integer.toString(query.limit()));
        ResponseEntity<String> response = goongClient.forward(AUTOCOMPLETE_PATH, params);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new PlaceLookupException("Goong autocomplete answered " + response.getStatusCode().value());
        }
        JsonNode predictions = read(response.getBody()).path("predictions");
        if (!predictions.isArray()) {
            return List.of();
        }
        List<PlaceHit> hits = new ArrayList<>();
        for (JsonNode prediction : predictions) {
            String placeId = text(prediction.path("place_id"));
            if (placeId == null) {
                continue;
            }
            String name = text(prediction.path("structured_formatting").path("main_text"));
            if (name == null) {
                name = text(prediction.path("name"));
            }
            hits.add(new PlaceHit(type(), placeId, name, text(prediction.path("description")),
                    null, null, null, null));
        }
        return hits;
    }

    @Override
    public Optional<PlaceHit> getDetails(String providerPlaceId) {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("place_id", providerPlaceId);
        ResponseEntity<String> response = goongClient.forward(DETAIL_PATH, params);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return Optional.empty();
        }
        JsonNode result = read(response.getBody()).path("result");
        if (!result.isObject()) {
            return Optional.empty();
        }
        JsonNode location = result.path("geometry").path("location");
        JsonNode types = result.path("types");
        String primaryType = types.isArray() && types.size() > 0 ? text(types.get(0)) : null;
        return Optional.of(new PlaceHit(type(), providerPlaceId, text(result.path("name")),
                text(result.path("formatted_address")), decimal(location.path("lat")), decimal(location.path("lng")),
                primaryType, null));
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new PlaceLookupException("Goong answered with a body that is not JSON", e);
        }
    }

    private String text(JsonNode node) {
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText().trim() : null;
    }

    private BigDecimal decimal(JsonNode node) {
        return node != null && node.isNumber() ? node.decimalValue() : null;
    }
}
