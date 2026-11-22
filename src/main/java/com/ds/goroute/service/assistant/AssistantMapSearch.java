package com.ds.goroute.service.assistant;

import com.ds.goroute.entity.Place;
import com.ds.goroute.repository.MapResponseCacheRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.mapsearch.MapText;
import com.ds.goroute.service.placelookup.PlaceHit;
import com.ds.goroute.service.placelookup.PlaceLookupException;
import com.ds.goroute.service.placelookup.PlaceLookupProvider;
import com.ds.goroute.service.placelookup.PlaceLookupProviderType;
import com.ds.goroute.service.placelookup.PlaceLookupProviders;
import com.ds.goroute.service.placelookup.PlaceLookupQuery;
import com.ds.goroute.service.placelookup.PlaceLookupRouter;
import com.ds.goroute.type.BusinessConfigKey;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * search_places' second source: the map provider the AI trip planner uses ({@link PlaceLookupRouter}),
 * asked when the catalogue has fewer than {@link #CATALOGUE_ENOUGH} places by the asked name. A
 * venue the user names ("lẩu bò Ban Công") usually exists even when the catalogue lacks it.
 *
 * <p>Asked with the model's own words (not the reduced name words: the street helps the map), at
 * the anchor, within the search radius. A hit is kept with a name and coordinates, within
 * {@code maxKm} of the anchor, and when it is not a catalogue row again (the same folded name, or
 * under {@link #DUPLICATE_METRES} from a catalogue row sharing a word of its name). Goong's
 * autocomplete has no coordinates, so its first {@link #MAX_DETAILS} hits are looked up one by one.
 *
 * <p>The provider's kept hits are cached for {@link #CACHE_TTL} in {@code map_response_cache}, so
 * the same question costs nothing the second time; catalogue twins are dropped on every read, so
 * a place added to the catalogue meanwhile shows as a catalogue row. A provider failure never
 * fails the tool: it is logged and the answer has no map rows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AssistantMapSearch {

    static final int CATALOGUE_ENOUGH = 3;
    static final int MAX_QUERY = 120;
    static final int MAX_HITS = 5;
    static final int MAX_DETAILS = 3;
    static final double DUPLICATE_METRES = 150;
    static final Duration CACHE_TTL = Duration.ofDays(7);
    static final String CACHE_OPERATION = "ASSISTANT_PLACES";
    /** Words this short ("ba", "le", "an") say nothing about two names being the same place. */
    private static final int MIN_SHARED_WORD = 3;
    private static final int COORDINATE_SCALE = 6;

    private final BusinessConfigService config;
    private final PlaceLookupRouter router;
    private final PlaceLookupProviders providers;
    private final MapResponseCacheRepository cache;
    private final ObjectMapper objectMapper;

    /** One lookup: the provider asked and the places kept, in the provider's order. */
    record Result(PlaceLookupProviderType provider, List<AssistantMapPlace> places) {
    }

    /**
     * @param catalogue the catalogue rows already in the answer, so the map does not repeat one
     * @return null when the lookup did not run: switched off, no query, or no provider with
     *         credentials for this point; a result with no places when it ran and found nothing
     */
    Result search(UUID userId, String rawQuery, BigDecimal lat, BigDecimal lng, double radiusKm, double maxKm,
                  Collection<Place> catalogue) {
        if (!config.getBoolean(BusinessConfigKey.ASSISTANT_MAP_SEARCH_ENABLED)) return null;
        String query = query(rawQuery);
        if (query == null || lat == null || lng == null) return null;
        PlaceLookupProviderType type = router.providerFor(userId, lat, lng);
        if (!providers.isAvailable(type)) return null;
        int radiusMetres = (int) Math.round(radiusKm * 1000);
        String key = MapText.key(CACHE_OPERATION, type, MapText.normalize(query),
                lat.setScale(3, RoundingMode.HALF_UP), lng.setScale(3, RoundingMode.HALF_UP), radiusMetres);
        List<AssistantMapPlace> hits = cached(key);
        if (hits == null) {
            try {
                hits = lookup(providers.get(type), query, lat, lng, radiusMetres, maxKm);
            } catch (RuntimeException e) {
                log.warn("Assistant map search via {} failed: {}", type, reason(e));
                return new Result(type, List.of());
            }
            store(key, type, hits);
        }
        return new Result(type, withoutCatalogueTwins(hits, lat, lng, maxKm, catalogue));
    }

    private List<AssistantMapPlace> lookup(PlaceLookupProvider provider, String query, BigDecimal lat, BigDecimal lng,
                                           int radiusMetres, double maxKm) {
        List<PlaceHit> hits = provider.searchText(new PlaceLookupQuery(query, lat, lng, radiusMetres, MAX_HITS));
        List<AssistantMapPlace> kept = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int index = 0;
        for (PlaceHit hit : hits) {
            if (index++ >= MAX_HITS) break;
            PlaceHit located = hit;
            if (hit.latitude() == null || hit.longitude() == null) {
                if (index > MAX_DETAILS || hit.providerPlaceId() == null) continue;
                located = details(provider, hit.providerPlaceId());
                if (located == null) continue;
            }
            AssistantMapPlace place = place(provider.type(), hit, located);
            if (place == null || distanceKm(lat, lng, place.lat(), place.lng()) > maxKm) continue;
            if (seen.add(place.providerPlaceId())) kept.add(place);
        }
        return kept;
    }

    private PlaceHit details(PlaceLookupProvider provider, String providerPlaceId) {
        try {
            return provider.getDetails(providerPlaceId).orElse(null);
        } catch (RuntimeException e) {
            log.warn("Assistant map search: {} details failed: {}", provider.type(), reason(e));
            return null;
        }
    }

    /** The search hit with what its details added; null without an id, a name or coordinates. */
    private static AssistantMapPlace place(PlaceLookupProviderType type, PlaceHit hit, PlaceHit located) {
        String id = AssistantMapPlace.trimmed(hit.providerPlaceId());
        String name = firstText(located.name(), hit.name());
        if (id == null || id.length() > AssistantMapPlace.MAX_ID || name == null
                || located.latitude() == null || located.longitude() == null) {
            return null;
        }
        String address = firstText(located.address(), hit.address());
        return new AssistantMapPlace(type.name(), id, cut(name, AssistantMapPlace.MAX_NAME),
                address == null ? null : cut(address, AssistantMapPlace.MAX_ADDRESS),
                located.latitude().setScale(COORDINATE_SCALE, RoundingMode.HALF_UP),
                located.longitude().setScale(COORDINATE_SCALE, RoundingMode.HALF_UP));
    }

    private static List<AssistantMapPlace> withoutCatalogueTwins(List<AssistantMapPlace> hits, BigDecimal lat,
                                                                 BigDecimal lng, double maxKm,
                                                                 Collection<Place> catalogue) {
        List<AssistantMapPlace> kept = new ArrayList<>();
        for (AssistantMapPlace hit : hits) {
            // A cached answer was kept for a rounded anchor; this one is the exact point.
            if (distanceKm(lat, lng, hit.lat(), hit.lng()) > maxKm) continue;
            if (!isCatalogueTwin(hit, catalogue)) kept.add(hit);
        }
        return kept;
    }

    static boolean isCatalogueTwin(AssistantMapPlace hit, Collection<Place> catalogue) {
        String name = MapText.normalize(hit.name());
        Set<String> words = words(name);
        for (Place place : catalogue) {
            String other = MapText.normalize(place.getTitle());
            if (!other.isEmpty() && other.equals(name)) return true;
            if (place.getLatitude() == null || place.getLongitude() == null) continue;
            boolean near = distanceKm(hit.lat(), hit.lng(), place.getLatitude(), place.getLongitude()) * 1000 < DUPLICATE_METRES;
            if (near && !Collections.disjoint(words, words(other))) return true;
        }
        return false;
    }

    private static Set<String> words(String folded) {
        Set<String> words = new HashSet<>();
        for (String word : folded.split(" ")) {
            if (word.length() >= MIN_SHARED_WORD) words.add(word);
        }
        return words;
    }

    // ---------------------------------------------------------------- cache

    private List<AssistantMapPlace> cached(String key) {
        try {
            String body = cache.find(key, LocalDateTime.now(ZoneOffset.UTC)).orElse(null);
            if (body == null) return null;
            List<Map<String, Object>> rows = objectMapper.readValue(body, new TypeReference<List<Map<String, Object>>>() {
            });
            List<AssistantMapPlace> places = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String provider = AssistantMapPlace.provider(text(row.get("provider")));
                String id = text(row.get("providerPlaceId"));
                String name = text(row.get("name"));
                BigDecimal lat = decimal(row.get("lat"));
                BigDecimal lng = decimal(row.get("lng"));
                if (name == null || !AssistantMapPlace.hasLocation(provider, id, lat, lng)) continue;
                places.add(new AssistantMapPlace(provider, id, name, text(row.get("address")), lat, lng));
            }
            return places;
        } catch (Exception e) {
            log.warn("Assistant map search: cache read failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private void store(String key, PlaceLookupProviderType type, List<AssistantMapPlace> places) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AssistantMapPlace place : places) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("provider", place.provider());
            row.put("providerPlaceId", place.providerPlaceId());
            row.put("name", place.name());
            if (place.address() != null) row.put("address", place.address());
            row.put("lat", place.lat().toPlainString());
            row.put("lng", place.lng().toPlainString());
            rows.add(row);
        }
        try {
            cache.save(key, CACHE_OPERATION, type.name(), objectMapper.writeValueAsString(rows),
                    LocalDateTime.now(ZoneOffset.UTC).plus(CACHE_TTL));
        } catch (Exception e) {
            log.warn("Assistant map search: cache write failed: {}", e.getClass().getSimpleName());
        }
    }

    // ---------------------------------------------------------------- helpers

    /** The model's query as it wrote it, trimmed and capped; null when there is none. */
    static String query(String raw) {
        String value = AssistantMapPlace.trimmed(raw);
        if (value == null) return null;
        value = value.replaceAll("\\s+", " ");
        return value.length() > MAX_QUERY ? value.substring(0, MAX_QUERY).trim() : value;
    }

    /** Provider messages carry a status at most; anything else is named by its type only. */
    private static String reason(RuntimeException e) {
        return e instanceof PlaceLookupException ? e.getMessage() : e.getClass().getSimpleName();
    }

    private static String firstText(String first, String second) {
        String value = AssistantMapPlace.trimmed(first);
        return value != null ? value : AssistantMapPlace.trimmed(second);
    }

    private static String cut(String value, int max) {
        return value.length() > max ? value.substring(0, max).trim() : value;
    }

    private static String text(Object value) {
        return value == null ? null : AssistantMapPlace.trimmed(value.toString());
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static double distanceKm(BigDecimal aLat, BigDecimal aLng, BigDecimal bLat, BigDecimal bLng) {
        double dLat = Math.toRadians(bLat.doubleValue() - aLat.doubleValue());
        double dLng = Math.toRadians(bLng.doubleValue() - aLng.doubleValue());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(aLat.doubleValue()))
                * Math.cos(Math.toRadians(bLat.doubleValue())) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
