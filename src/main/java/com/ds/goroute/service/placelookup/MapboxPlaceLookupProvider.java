package com.ds.goroute.service.placelookup;

import com.ds.goroute.entity.MapPlaceCacheEntry;
import com.ds.goroute.mapper.MapCacheMapper;
import com.ds.goroute.service.mapsearch.MapBudgetGuard;
import com.ds.goroute.service.mapsearch.MapboxResults;
import com.ds.goroute.thirdparty.mapbox.MapboxClient;
import com.ds.goroute.thirdparty.mapbox.MapboxException;
import com.ds.goroute.type.MapBillingApi;
import com.ds.goroute.type.MapProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Mapbox Search Box as a {@link PlaceLookupProvider}. A search hit already carries coordinates,
 * and every hit is kept in {@code map_place_cache}, so {@link #getDetails(String)} for a hit is
 * answered from there without a second paid call. Calls spend the system map budget.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MapboxPlaceLookupProvider implements PlaceLookupProvider {

    private final MapboxClient mapbox;
    private final MapBudgetGuard budget;
    private final MapCacheMapper cache;

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
        if (!budget.tryAcquire(MapBillingApi.SEARCH).allowed()) {
            throw new PlaceLookupException("Mapbox budget for this period is spent");
        }
        Double latitude = query.hasBias() ? query.biasLatitude().doubleValue() : null;
        Double longitude = query.hasBias() ? query.biasLongitude().doubleValue() : null;
        // Vietnamese names inside Vietnam; elsewhere the place's own name, which is what Web
        // research quotes.
        String language = latitude != null && longitude != null
                && latitude >= 8.0 && latitude <= 23.5 && longitude >= 102.0 && longitude <= 110.5 ? "vi" : null;
        try {
            List<MapPlaceCacheEntry> places = MapboxResults.places(
                    mapbox.forward(query.text(), latitude, longitude, language, Math.min(query.limit(), 10)));
            places.forEach(this::remember);
            return places.stream().map(this::hit).toList();
        } catch (MapboxException exception) {
            throw new PlaceLookupException("Mapbox search failed: " + exception.getMessage(), exception);
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
