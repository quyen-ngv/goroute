package com.ds.goroute.service.mapsearch;

import com.ds.goroute.entity.MapPlaceCacheEntry;
import com.ds.goroute.type.MapProvider;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads Mapbox Search Box and Geocoding v6 features into {@link MapPlaceCacheEntry} rows, and
 * cleans a result list the way the Vietnam probe (2026-10-11) showed it needs: the same place
 * repeated at one spot, and Chinese names leaking into Vietnamese answers.
 */
public final class MapboxResults {

    /** Two results with the same name closer than this are one place listed twice. */
    static final double DUPLICATE_RADIUS_M = 30;
    private static final Set<String> CJK_LANGUAGES = Set.of("ja", "ko", "zh");

    private MapboxResults() {
    }

    public static List<MapPlaceCacheEntry> places(JsonNode featureCollection) {
        List<MapPlaceCacheEntry> places = new ArrayList<>();
        JsonNode features = featureCollection == null ? null : featureCollection.path("features");
        if (features == null || !features.isArray()) {
            return places;
        }
        for (JsonNode feature : features) {
            MapPlaceCacheEntry place = place(feature);
            if (place != null) {
                places.add(place);
            }
        }
        return places;
    }

    /** One feature as a cache row; null when it lacks an ID, a name or a position. */
    public static MapPlaceCacheEntry place(JsonNode feature) {
        JsonNode properties = feature.path("properties");
        String id = text(properties, "mapbox_id");
        String name = text(properties, "name");
        Double latitude = number(properties.path("coordinates"), "latitude");
        Double longitude = number(properties.path("coordinates"), "longitude");
        JsonNode point = feature.path("geometry").path("coordinates");
        if ((latitude == null || longitude == null) && point.isArray() && point.size() >= 2) {
            longitude = point.get(0).asDouble();
            latitude = point.get(1).asDouble();
        }
        if (id == null || name == null || latitude == null || longitude == null) {
            return null;
        }
        return MapPlaceCacheEntry.builder()
                .provider(MapProvider.MAPBOX.name())
                .providerPlaceId(id)
                .name(name)
                .nameNormalized(MapText.normalize(name))
                .address(address(properties, name))
                .latitude(latitude)
                .longitude(longitude)
                .categories(categories(properties))
                .phone(text(properties.path("metadata"), "phone"))
                .website(text(properties.path("metadata"), "website"))
                .countryCode(upper(text(properties.path("context").path("country"), "country_code")))
                .pickedCount(0)
                .build();
    }

    /**
     * Drops names in a script the reader did not ask for ("還劍湖" in a Vietnamese answer) and
     * the second of two same-named results within {@link #DUPLICATE_RADIUS_M}.
     */
    public static List<MapPlaceCacheEntry> clean(List<MapPlaceCacheEntry> places, String language) {
        boolean cjkAllowed = language != null && CJK_LANGUAGES.contains(language);
        List<MapPlaceCacheEntry> kept = new ArrayList<>();
        for (MapPlaceCacheEntry place : places) {
            if (!cjkAllowed && MapText.hasCjk(place.getName())) {
                continue;
            }
            boolean duplicate = kept.stream().anyMatch(other ->
                    other.getNameNormalized().equals(place.getNameNormalized())
                            && distanceMeters(other.getLatitude(), other.getLongitude(),
                            place.getLatitude(), place.getLongitude()) < DUPLICATE_RADIUS_M);
            if (!duplicate) {
                kept.add(place);
            }
        }
        return kept;
    }

    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double earthRadius = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * earthRadius * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String address(JsonNode properties, String name) {
        String full = text(properties, "full_address");
        if (full != null) {
            return full;
        }
        String formatted = text(properties, "place_formatted");
        return formatted == null ? null : name + ", " + formatted;
    }

    /** POI categories when there are any, otherwise the feature type ("place", "street"). */
    private static String categories(JsonNode properties) {
        JsonNode categories = properties.path("poi_category");
        if (categories.isArray() && !categories.isEmpty()) {
            List<String> names = new ArrayList<>();
            categories.forEach(category -> names.add(category.asText()));
            return truncate(String.join(", ", names), 500);
        }
        return text(properties, "feature_type");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static Double number(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isNumber() ? value.asDouble() : null;
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
