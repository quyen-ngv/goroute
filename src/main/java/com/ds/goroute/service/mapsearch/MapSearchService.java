package com.ds.goroute.service.mapsearch;

import com.ds.goroute.entity.MapPlaceCacheEntry;
import com.ds.goroute.entity.Ward;
import com.ds.goroute.mapper.MapCacheMapper;
import com.ds.goroute.service.GeoService;
import com.ds.goroute.thirdparty.goong.GoongClient;
import com.ds.goroute.thirdparty.mapbox.MapboxClient;
import com.ds.goroute.thirdparty.mapbox.MapboxException;
import com.ds.goroute.type.MapBillingApi;
import com.ds.goroute.type.MapProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Map search, place detail and geocoding for {@code /v1/api/goong/*}, answered by the provider
 * the caller's cohort uses, always in Goong's JSON shape (see {@link GoongShape}).
 *
 * <p>Goong calls are forwarded exactly as before. Mapbox calls go through, in order:
 * <ol>
 *   <li>the answer cache ({@code map_response_cache}) -- the same query from anyone;</li>
 *   <li>places users already chose ({@code map_place_cache}, picked) -- autocomplete only;</li>
 *   <li>the system budget ({@link MapBudgetGuard}) -- refused means saving mode: cached and
 *       picked places only, flagged {@code saving_mode};</li>
 *   <li>Mapbox, and on failure Goong when the point is in Vietnam.</li>
 * </ol>
 * Every place Mapbox returns is kept in {@code map_place_cache}, so choosing a result -- a
 * place-detail call -- costs nothing.
 */
@Slf4j
@Service
public class MapSearchService {

    static final Duration AUTOCOMPLETE_TTL = Duration.ofDays(7);
    static final Duration REVERSE_TTL = Duration.ofDays(30);
    static final Duration GEOCODE_TTL = Duration.ofDays(90);
    /** Reverse answers are shared by everyone in a cell of 1/2000 degree (~55 m of latitude). */
    static final int REVERSE_GRID_PER_DEGREE = 2000;
    /** Picked places answer autocomplete alone only when there are this many good ones. */
    static final int PICKED_ENOUGH = 5;
    static final double PICKED_RADIUS_KM = 50;
    static final int MAX_QUERY_LENGTH = 100;
    static final int MIN_QUERY_LENGTH = 2;
    private static final Pattern MAPBOX_ID = Pattern.compile("[A-Za-z0-9._=-]{1,255}");

    private final MapProviderResolver resolver;
    private final MapboxClient mapbox;
    private final GoongClient goong;
    private final MapBudgetGuard budget;
    private final MapCacheMapper cache;
    private final GeoService geoService;
    private final ObjectMapper objectMapper;
    private final GoongShape shape;
    private final Clock clock;

    @Autowired
    public MapSearchService(MapProviderResolver resolver, MapboxClient mapbox, GoongClient goong,
                            MapBudgetGuard budget, MapCacheMapper cache, GeoService geoService,
                            ObjectMapper objectMapper) {
        this(resolver, mapbox, goong, budget, cache, geoService, objectMapper, Clock.systemUTC());
    }

    MapSearchService(MapProviderResolver resolver, MapboxClient mapbox, GoongClient goong,
                     MapBudgetGuard budget, MapCacheMapper cache, GeoService geoService,
                     ObjectMapper objectMapper, Clock clock) {
        this.resolver = resolver;
        this.mapbox = mapbox;
        this.goong = goong;
        this.budget = budget;
        this.cache = cache;
        this.geoService = geoService;
        this.objectMapper = objectMapper;
        this.shape = new GoongShape(objectMapper);
        this.clock = clock;
    }

    /** Parameters of an autocomplete call, as the app sends them to the Goong proxy. */
    public record AutocompleteQuery(String input, String location, Integer radiusKm, Integer limit,
                                    String sessionToken, Boolean moreCompound, String language) {
    }

    // --- autocomplete -------------------------------------------------------------------

    public ResponseEntity<String> autocomplete(UUID userId, AutocompleteQuery query) {
        Optional<MapProvider> provider = resolver.forUser(userId);
        if (provider.isPresent() && provider.get() == MapProvider.GOONG) {
            return goongAutocomplete(query);
        }
        return mapboxAutocomplete(query, provider.isEmpty());
    }

    private ResponseEntity<String> mapboxAutocomplete(AutocompleteQuery query, boolean catalogueOnly) {
        String input = query.input() == null ? "" : query.input().trim();
        if (input.length() > MAX_QUERY_LENGTH) {
            input = input.substring(0, MAX_QUERY_LENGTH);
        }
        if (input.length() < MIN_QUERY_LENGTH) {
            return json(shape.autocomplete(List.of()));
        }
        double[] point = parsePoint(query.location());
        Double latitude = point == null ? null : point[0];
        Double longitude = point == null ? null : point[1];
        Integer radiusKm = query.radiusKm() != null && query.radiusKm() > 0 && point != null ? query.radiusKm() : null;
        int limit = clamp(query.limit() == null ? 5 : query.limit(), 1, 10);
        String language = language(query.language());
        String normalized = MapText.normalize(input);
        String cacheKey = MapText.key("AUTOCOMPLETE", normalized.isEmpty() ? input.toLowerCase(Locale.ROOT) : normalized,
                round(latitude, 2), round(longitude, 2), radiusKm, limit, language);

        Optional<String> cached = cachedResponse(cacheKey);
        if (cached.isPresent()) {
            return jsonText(cached.get());
        }
        List<MapPlaceCacheEntry> picked = pickedPlaces(normalized, latitude, longitude, radiusKm, limit);
        if (picked.size() >= Math.min(limit, PICKED_ENOUGH) || catalogueOnly) {
            return json(shape.autocomplete(picked));
        }
        MapBudgetGuard.Decision decision = budget.tryAcquire(MapBillingApi.SEARCH);
        if (!decision.allowed()) {
            return json(GoongShape.savingMode(shape.autocomplete(picked), decision.resumesAt()));
        }
        try {
            int asked = radiusKm == null ? limit : 10;
            List<MapPlaceCacheEntry> places = MapboxResults.clean(
                    MapboxResults.places(mapbox.forward(input, latitude, longitude, language, asked)), language);
            if (radiusKm != null) {
                double maxMeters = radiusKm * 1000.0;
                places = places.stream()
                        .filter(place -> MapboxResults.distanceMeters(latitude, longitude,
                                place.getLatitude(), place.getLongitude()) <= maxMeters)
                        .toList();
            }
            places = places.stream().limit(limit).toList();
            places.forEach(this::rememberPlace);
            ObjectNode answer = shape.autocomplete(places);
            storeResponse(cacheKey, "AUTOCOMPLETE", answer, AUTOCOMPLETE_TTL);
            return json(answer);
        } catch (MapboxException exception) {
            log.warn("Mapbox autocomplete failed ({}); {}", exception.getMessage(),
                    canFallBack(latitude, longitude) ? "asking Goong" : "no fallback");
            if (canFallBack(latitude, longitude)) {
                return goongAutocomplete(query);
            }
            return picked.isEmpty() ? upstreamError() : json(shape.autocomplete(picked));
        }
    }

    private ResponseEntity<String> goongAutocomplete(AutocompleteQuery query) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("input", query.input());
        if (query.location() != null) params.add("location", query.location());
        if (query.radiusKm() != null) params.add("radius", String.valueOf(query.radiusKm()));
        if (query.limit() != null) params.add("limit", String.valueOf(query.limit()));
        if (query.sessionToken() != null) params.add("sessiontoken", query.sessionToken());
        if (query.moreCompound() != null) params.add("more_compound", String.valueOf(query.moreCompound()));
        return goong.forward("/Place/AutoComplete", params);
    }

    // --- place detail -------------------------------------------------------------------

    /**
     * A Mapbox ID ({@code mapbox:...}) is answered by Mapbox's data whatever the caller's
     * cohort is now -- the ID came from an earlier answer. Any other ID is Goong's.
     */
    public ResponseEntity<String> placeDetail(String placeId, String sessionToken, String acceptLanguage) {
        if (placeId == null || !placeId.startsWith(GoongShape.MAPBOX_ID_PREFIX)) {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("place_id", placeId);
            if (sessionToken != null) params.add("sessiontoken", sessionToken);
            return goong.forward("/Place/Detail", params);
        }
        String id = placeId.substring(GoongShape.MAPBOX_ID_PREFIX.length());
        if (!MAPBOX_ID.matcher(id).matches()) {
            return json(shape.emptyDetail());
        }
        MapPlaceCacheEntry place = findPlace(id);
        if (place == null) {
            if (!mapbox.isConfigured()) {
                return json(shape.emptyDetail());
            }
            MapBudgetGuard.Decision decision = budget.tryAcquire(MapBillingApi.SEARCH);
            if (!decision.allowed()) {
                return json(GoongShape.savingMode(shape.emptyDetail(), decision.resumesAt()));
            }
            try {
                List<MapPlaceCacheEntry> found = MapboxResults.places(mapbox.retrieve(id, language(acceptLanguage)));
                if (found.isEmpty()) {
                    return json(shape.emptyDetail());
                }
                place = found.get(0);
                rememberPlace(place);
            } catch (MapboxException exception) {
                log.warn("Mapbox retrieve failed for {}: {}", id, exception.getMessage());
                return upstreamError();
            }
        }
        markPicked(place);
        return json(shape.detail(place, compound(place.getLatitude(), place.getLongitude())));
    }

    // --- geocode ------------------------------------------------------------------------

    public ResponseEntity<String> geocode(UUID userId, String address, String latlng, Integer limit,
                                          String acceptLanguage) {
        Optional<MapProvider> provider = resolver.forUser(userId);
        if (provider.isPresent() && provider.get() == MapProvider.GOONG) {
            return goongGeocode(address, latlng, limit);
        }
        boolean catalogueOnly = provider.isEmpty();
        String language = language(acceptLanguage);
        if (latlng != null) {
            double[] point = parsePoint(latlng);
            if (point == null) {
                return json(shape.geocode(List.of(), null));
            }
            return reverse(point[0], point[1], limit, language, catalogueOnly, () -> goongGeocode(null, latlng, limit));
        }
        if (address == null || address.isBlank()) {
            return json(shape.geocode(List.of(), null));
        }
        return forwardGeocode(address.trim(), language, catalogueOnly, () -> goongGeocode(address, null, limit));
    }

    /** Reverse geocoding for server-side callers (tracker stop names), in the owner's cohort. */
    public ResponseEntity<String> reverseForServer(UUID ownerId, double latitude, double longitude, int limit) {
        String latlng = latitude + "," + longitude;
        return geocode(ownerId, null, latlng, limit, null);
    }

    private ResponseEntity<String> reverse(double latitude, double longitude, Integer limit, String language,
                                           boolean catalogueOnly, java.util.function.Supplier<ResponseEntity<String>> fallback) {
        int size = clamp(limit == null ? 1 : limit, 1, 5);
        double cellLat = snap(latitude);
        double cellLng = snap(longitude);
        String cacheKey = MapText.key("REVERSE", cellLat, cellLng, size, language);
        Optional<String> cached = cachedResponse(cacheKey);
        if (cached.isPresent()) {
            return jsonText(cached.get());
        }
        GoongShape.Compound compound = compound(latitude, longitude);
        if (catalogueOnly) {
            return json(shape.geocode(List.of(), compound));
        }
        MapBudgetGuard.Decision decision = budget.tryAcquire(MapBillingApi.SEARCH);
        if (!decision.allowed()) {
            return json(GoongShape.savingMode(shape.geocode(List.of(), compound), decision.resumesAt()));
        }
        try {
            List<MapPlaceCacheEntry> places = MapboxResults.clean(
                    MapboxResults.places(mapbox.reverse(cellLat, cellLng, language, size)), language);
            if (places.isEmpty() && canFallBack(latitude, longitude)) {
                return fallback.get();
            }
            places.forEach(this::rememberPlace);
            ObjectNode answer = shape.geocode(places, compound);
            storeResponse(cacheKey, "REVERSE", answer, REVERSE_TTL);
            return json(answer);
        } catch (MapboxException exception) {
            log.warn("Mapbox reverse failed ({})", exception.getMessage());
            return canFallBack(latitude, longitude) ? fallback.get() : upstreamError();
        }
    }

    private ResponseEntity<String> forwardGeocode(String address, String language, boolean catalogueOnly,
                                                  java.util.function.Supplier<ResponseEntity<String>> fallback) {
        String query = address.length() > MAX_QUERY_LENGTH ? address.substring(0, MAX_QUERY_LENGTH) : address;
        String cacheKey = MapText.key("GEOCODE", MapText.normalize(query), language);
        Optional<String> cached = cachedResponse(cacheKey);
        if (cached.isPresent()) {
            return jsonText(cached.get());
        }
        if (catalogueOnly) {
            return json(shape.geocode(List.of(), null));
        }
        MapBudgetGuard.Decision decision = budget.tryAcquire(MapBillingApi.GEOCODE);
        if (!decision.allowed()) {
            return json(GoongShape.savingMode(shape.geocode(List.of(), null), decision.resumesAt()));
        }
        try {
            List<MapPlaceCacheEntry> places = MapboxResults.places(mapbox.geocodeForward(query, language, 1));
            ObjectNode answer = shape.geocode(places, null);
            storeResponse(cacheKey, "GEOCODE", answer, GEOCODE_TTL);
            return json(answer);
        } catch (MapboxException exception) {
            log.warn("Mapbox geocode failed ({})", exception.getMessage());
            return resolver.mayFallBackToGoong() ? fallback.get() : upstreamError();
        }
    }

    private ResponseEntity<String> goongGeocode(String address, String latlng, Integer limit) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (address != null) params.add("address", address);
        if (latlng != null) params.add("latlng", latlng);
        // Undocumented on Goong's side but honoured: without it reverse geocoding answers
        // with a single result. Goong caps the value at five.
        if (limit != null) params.add("limit", String.valueOf(limit));
        return goong.forward("/Geocode", params);
    }

    // --- caches -------------------------------------------------------------------------

    private List<MapPlaceCacheEntry> pickedPlaces(String normalized, Double latitude, Double longitude,
                                                  Integer radiusKm, int limit) {
        if (normalized.length() < 3) {
            return List.of();
        }
        try {
            double radius = radiusKm != null ? radiusKm : PICKED_RADIUS_KM;
            return cache.searchPicked(normalized, latitude, longitude, radius, limit);
        } catch (RuntimeException exception) {
            log.warn("Picked-place lookup failed: {}", exception.getMessage());
            return List.of();
        }
    }

    private MapPlaceCacheEntry findPlace(String id) {
        try {
            return cache.findPlace(MapProvider.MAPBOX.name(), id);
        } catch (RuntimeException exception) {
            log.warn("Place cache read failed for {}: {}", id, exception.getMessage());
            return null;
        }
    }

    private void rememberPlace(MapPlaceCacheEntry place) {
        try {
            cache.upsertPlace(place);
        } catch (RuntimeException exception) {
            log.warn("Place cache write failed for {}: {}", place.getProviderPlaceId(), exception.getMessage());
        }
    }

    private void markPicked(MapPlaceCacheEntry place) {
        try {
            cache.incrementPicked(place.getProvider(), place.getProviderPlaceId());
        } catch (RuntimeException exception) {
            log.warn("Could not mark {} picked: {}", place.getProviderPlaceId(), exception.getMessage());
        }
    }

    private Optional<String> cachedResponse(String cacheKey) {
        try {
            return Optional.ofNullable(cache.findResponse(cacheKey, LocalDateTime.now(clock)));
        } catch (RuntimeException exception) {
            log.warn("Answer cache read failed: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private void storeResponse(String cacheKey, String operation, ObjectNode answer, Duration ttl) {
        try {
            cache.upsertResponse(cacheKey, operation, MapProvider.MAPBOX.name(),
                    objectMapper.writeValueAsString(answer), LocalDateTime.now(clock).plus(ttl));
        } catch (JsonProcessingException | RuntimeException exception) {
            log.warn("Answer cache write failed: {}", exception.getMessage());
        }
    }

    /** Ward and province from the boundary dataset; null outside Vietnam or before it is loaded. */
    private GoongShape.Compound compound(double latitude, double longitude) {
        try {
            return geoService.resolveWard(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude))
                    .map(MapSearchService::compoundOf)
                    .orElse(null);
        } catch (RuntimeException exception) {
            log.warn("Ward lookup failed: {}", exception.getMessage());
            return null;
        }
    }

    private static GoongShape.Compound compoundOf(Ward ward) {
        return new GoongShape.Compound(ward.getName(), null, ward.getProvinceName());
    }

    // --- helpers ------------------------------------------------------------------------

    private boolean canFallBack(Double latitude, Double longitude) {
        return resolver.mayFallBackToGoong() && (latitude == null || longitude == null || inVietnam(latitude, longitude));
    }

    /** Vietnam's mainland and near islands; Goong has nothing outside it. */
    static boolean inVietnam(double latitude, double longitude) {
        return latitude >= 8.0 && latitude <= 23.5 && longitude >= 102.0 && longitude <= 110.5;
    }

    /** "lat,lng" as the app sends {@code location} and {@code latlng}; null when malformed. */
    static double[] parsePoint(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            double latitude = Double.parseDouble(parts[0].trim());
            double longitude = Double.parseDouble(parts[1].trim());
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180
                    || Double.isNaN(latitude) || Double.isNaN(longitude)) {
                return null;
            }
            return new double[]{latitude, longitude};
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** Primary language subtag from Accept-Language ("vi-VN,vi;q=0.9" -> "vi"); Vietnamese by default. */
    static String language(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return "vi";
        }
        String first = acceptLanguage.split(",")[0].split(";")[0].trim();
        String primary = first.split("[-_]")[0].toLowerCase(Locale.ROOT);
        return primary.matches("[a-z]{2,3}") ? primary : "vi";
    }

    private static double snap(double value) {
        // Divide whole cells by an integer so the result is the nearest double to the cell edge,
        // the same value every time, which keeps cache keys and upstream URLs identical.
        return Math.round(value * REVERSE_GRID_PER_DEGREE) / (double) REVERSE_GRID_PER_DEGREE;
    }

    private static String round(Double value, int decimals) {
        return value == null ? "" : String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private ResponseEntity<String> json(JsonNode node) {
        try {
            return jsonText(objectMapper.writeValueAsString(node));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not write a map answer", exception);
        }
    }

    private static ResponseEntity<String> jsonText(String body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static ResponseEntity<String> upstreamError() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":\"UPSTREAM\",\"message\":\"Map provider request failed\"}}");
    }
}
