package com.ds.goroute.service.track;

import com.ds.goroute.dto.PlaceSearchCriteria;
import com.ds.goroute.entity.Place;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.PlaceSearchIndexService;
import com.ds.goroute.service.mapsearch.MapSearchService;
import com.ds.goroute.utils.GeoDistance;
import com.ds.goroute.type.PlaceSearchSort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Names an off-plan stop (TR-16): the nearest catalogue place first, the map provider's reverse
 * geocode second (Goong or Mapbox, by the track owner's cohort; see {@link MapSearchService}).
 * Neither lookup may fail an upload; every failure is logged and answered with
 * "no name".
 */
@Component
@Slf4j
public class TrackStopNamer {

    /** The map provider gets this long; a stop without a name is better than a slow upload. */
    static final long GEOCODE_TIMEOUT_MS = 2_000;
    /** Candidates asked from the index; the nearest of them wins. */
    private static final int CATALOG_CANDIDATES = 5;
    private static final int MAX_NAME_LENGTH = 255;

    private final PlaceSearchIndexService placeSearchIndexService;
    private final PlaceRepository placeRepository;
    private final MapSearchService mapSearchService;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public TrackStopNamer(PlaceSearchIndexService placeSearchIndexService,
                          PlaceRepository placeRepository,
                          MapSearchService mapSearchService,
                          ObjectMapper objectMapper,
                          @Qualifier("applicationTaskExecutor") Executor executor) {
        this.placeSearchIndexService = placeSearchIndexService;
        this.placeRepository = placeRepository;
        this.mapSearchService = mapSearchService;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    /** An active catalogue place near a stop. */
    public record CatalogPlace(UUID placeId, String name) {
    }

    public Optional<CatalogPlace> nearestCatalogPlace(double latitude, double longitude, int radiusM) {
        try {
            List<UUID> ids = placeSearchIndexService.searchPlaceIds(new PlaceSearchCriteria(
                    null, BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude),
                    BigDecimal.valueOf(radiusM / 1000d), null, null, null, null, null, null, null,
                    false, PlaceSearchSort.RELEVANCE, null, 0, CATALOG_CANDIDATES));
            if (ids.isEmpty()) {
                return Optional.empty();
            }
            Place nearest = null;
            double nearestMetres = Double.MAX_VALUE;
            for (Place place : placeRepository.findByIds(ids)) {
                if (place.getLatitude() == null || place.getLongitude() == null
                        || place.getTitle() == null || place.getTitle().isBlank()) {
                    continue;
                }
                double metres = GeoDistance.between(latitude, longitude,
                        place.getLatitude().doubleValue(), place.getLongitude().doubleValue());
                if (metres <= radiusM && metres < nearestMetres) {
                    nearest = place;
                    nearestMetres = metres;
                }
            }
            return nearest == null
                    ? Optional.empty()
                    : Optional.of(new CatalogPlace(nearest.getId(), truncate(nearest.getTitle().trim())));
        } catch (Exception exception) {
            log.warn("Catalogue lookup for an off-plan stop failed: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    /** The provider's name for the spot, or null (no key, timeout, error, empty answer, budget spent). */
    public String reverseGeocode(UUID ownerId, double latitude, double longitude) {
        CompletableFuture<ResponseEntity<String>> call = CompletableFuture.supplyAsync(
                () -> mapSearchService.reverseForServer(ownerId, latitude, longitude, 1), executor);
        try {
            ResponseEntity<String> response = call.get(GEOCODE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            return nameOf(objectMapper.readTree(response.getBody()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception exception) {
            call.cancel(true);
            log.warn("Reverse geocode for an off-plan stop failed: {}", exception.toString());
            return null;
        }
    }

    /** Runs naming work off the request thread. */
    public void submit(Runnable task) {
        executor.execute(task);
    }

    static String nameOf(JsonNode body) {
        JsonNode results = body.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return null;
        }
        JsonNode first = results.get(0);
        for (String field : List.of("name", "formatted_address")) {
            String value = first.path(field).asText("").trim();
            if (!value.isEmpty()) {
                return truncate(value);
            }
        }
        return null;
    }

    private static String truncate(String value) {
        return value.length() <= MAX_NAME_LENGTH ? value : value.substring(0, MAX_NAME_LENGTH);
    }
}
