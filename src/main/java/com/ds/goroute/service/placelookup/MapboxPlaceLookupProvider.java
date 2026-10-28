package com.ds.goroute.service.placelookup;

import com.ds.goroute.entity.MapPlaceCacheEntry;
import com.ds.goroute.mapper.MapCacheMapper;
import com.ds.goroute.service.mapsearch.MapBudgetGuard;
import com.ds.goroute.service.mapsearch.MapText;
import com.ds.goroute.service.mapsearch.MapboxResults;
import com.ds.goroute.thirdparty.mapbox.MapboxClient;
import com.ds.goroute.thirdparty.mapbox.MapboxException;
import com.ds.goroute.type.MapBillingApi;
import com.ds.goroute.type.MapProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Mapbox Search Box as a {@link PlaceLookupProvider}. A search hit already carries coordinates,
 * and every hit is kept in {@code map_place_cache}, so {@link #getDetails(String)} for a hit is
 * answered from there without a second paid call. A search answer is kept in
 * {@code map_response_cache} for {@link #SEARCH_TTL}, so the same text near the same point is paid
 * for once whoever asks (social videos, AI trips). Paid calls spend the system map budget.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MapboxPlaceLookupProvider implements PlaceLookupProvider {

    static final Duration SEARCH_TTL = Duration.ofDays(7);
    private static final String SEARCH_OPERATION = "PLACE_LOOKUP";

    private final MapboxClient mapbox;
    private final MapBudgetGuard budget;
    private final MapCacheMapper cache;
    private final ObjectMapper objectMapper;

    @Override
    public PlaceLookupProviderType type() {
        return PlaceLookupProviderType.MAPBOX;
    }

    @Override
    public boolean isAvailable() {
        return mapbox.isConfigured();
    }

    @Override
    public List<PlaceHit> searchText(PlaceLookupQuery query) {
        Double latitude = query.hasBias() ? query.biasLatitude().doubleValue() : null;
        Double longitude = query.hasBias() ? query.biasLongitude().doubleValue() : null;
        // Vietnamese names inside Vietnam; elsewhere the place's own name, which is what Web
        // research quotes.
        String language = latitude != null && longitude != null
                && latitude >= 8.0 && latitude <= 23.5 && longitude >= 102.0 && longitude <= 110.5 ? "vi" : null;
        int limit = Math.min(query.limit(), 10);
        String cacheKey = MapText.key(SEARCH_OPERATION, MapText.normalize(query.text()), latitude, longitude, language, limit);
        JsonNode cached = cachedAnswer(cacheKey);
        if (cached != null) {
            return MapboxResults.places(cached).stream().map(this::hit).toList();
        }
        if (!budget.tryAcquire(MapBillingApi.SEARCH).allowed()) {
            throw new PlaceLookupException("Mapbox budget for this period is spent");
        }
        try {
            JsonNode answer = mapbox.forward(query.text(), latitude, longitude, language, limit);
            storeAnswer(cacheKey, answer);
            List<MapPlaceCacheEntry> places = MapboxResults.places(answer);
            places.forEach(this::remember);
            return places.stream().map(this::hit).toList();
        } catch (MapboxException exception) {
            throw new PlaceLookupException("Mapbox search failed: " + exception.getMessage(), exception);
        }
    }

    private JsonNode cachedAnswer(String cacheKey) {
        try {
            String body = cache.findResponse(cacheKey, LocalDateTime.now(ZoneOffset.UTC));
            return body == null ? null : objectMapper.readTree(body);
        } catch (JsonProcessingException | RuntimeException exception) {
            log.warn("Answer cache read failed: {}", exception.getMessage());
            return null;
        }
    }

    private void storeAnswer(String cacheKey, JsonNode answer) {
        if (answer == null) {
            return;
        }
        try {
            cache.upsertResponse(cacheKey, SEARCH_OPERATION, MapProvider.MAPBOX.name(),
                    objectMapper.writeValueAsString(answer), LocalDateTime.now(ZoneOffset.UTC).plus(SEARCH_TTL));
        } catch (JsonProcessingException | RuntimeException exception) {
            log.warn("Answer cache write failed: {}", exception.getMessage());
        }
    }

    @Override
    public Optional<PlaceHit> getDetails(String providerPlaceId) {
        MapPlaceCacheEntry cached = cache.findPlace(MapProvider.MAPBOX.name(), providerPlaceId);
        if (cached != null) {
            return Optional.of(hit(cached));
        }
        if (!budget.tryAcquire(MapBillingApi.SEARCH).allowed()) {
            throw new PlaceLookupException("Mapbox budget for this period is spent");
        }
        try {
            List<MapPlaceCacheEntry> places = MapboxResults.places(mapbox.retrieve(providerPlaceId, null));
            places.forEach(this::remember);
            return places.stream().findFirst().map(this::hit);
        } catch (MapboxException exception) {
            throw new PlaceLookupException("Mapbox retrieve failed: " + exception.getMessage(), exception);
        }
    }

    private void remember(MapPlaceCacheEntry place) {
        try {
            cache.upsertPlace(place);
        } catch (RuntimeException exception) {
            log.warn("Place cache write failed for {}: {}", place.getProviderPlaceId(), exception.getMessage());
        }
    }

    private PlaceHit hit(MapPlaceCacheEntry place) {
        String primaryType = place.getCategories() == null ? null : place.getCategories().split(",")[0].trim();
        return new PlaceHit(type(), place.getProviderPlaceId(), place.getName(), place.getAddress(),
                BigDecimal.valueOf(place.getLatitude()), BigDecimal.valueOf(place.getLongitude()), primaryType, null);
    }
}
