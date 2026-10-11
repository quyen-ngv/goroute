package com.ds.goroute.service.mapsearch;

import com.ds.goroute.entity.MapPlaceCacheEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Builds answers in Goong's JSON shape from cached places, whatever provider found them.
 *
 * <p>Every installed app and the console parse {@code /v1/api/goong/*} as Goong JSON:
 * {@code predictions[]} for autocomplete, {@code result} for place detail, {@code results[]}
 * with {@code compound} for geocoding. Keeping that shape is what lets the provider change on
 * the server without an app release. Extra fields (a prediction's position, saving mode) are
 * ignored by apps that do not know them.
 */
public final class GoongShape {

    /** Prefix of every Mapbox place ID handed to clients; place detail routes on it. */
    public static final String MAPBOX_ID_PREFIX = "mapbox:";

    /** Administrative names for one point: ward ("commune" in Goong's words), district, province. */
    public record Compound(String commune, String district, String province) {
    }

    private final ObjectMapper objectMapper;

    public GoongShape(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode autocomplete(List<MapPlaceCacheEntry> places) {
        ObjectNode root = ok();
        ArrayNode predictions = root.putArray("predictions");
        for (MapPlaceCacheEntry place : places) {
            ObjectNode prediction = predictions.addObject();
            String secondary = secondaryText(place);
            prediction.put("place_id", clientId(place));
            prediction.put("description", secondary == null ? place.getName() : place.getName() + ", " + secondary);
            ObjectNode formatting = prediction.putObject("structured_formatting");
            formatting.put("main_text", place.getName());
            formatting.put("secondary_text", secondary == null ? "" : secondary);
            location(prediction, place);
            types(prediction, place);
        }
        return root;
    }

    public ObjectNode detail(MapPlaceCacheEntry place, Compound compound) {
        ObjectNode root = ok();
        root.set("result", result(place, compound));
        return root;
    }

    /** Detail for an ID nobody can resolve: Goong's own answer for an unknown place. */
    public ObjectNode emptyDetail() {
        ObjectNode root = ok();
        root.putNull("result");
        return root;
    }

    public ObjectNode geocode(List<MapPlaceCacheEntry> places, Compound compound) {
        ObjectNode root = ok();
        ArrayNode results = root.putArray("results");
        for (MapPlaceCacheEntry place : places) {
            results.add(result(place, compound));
        }
        return root;
    }

    /** Marks an answer as given from caches only, and says when live results come back. */
    public static ObjectNode savingMode(ObjectNode root, Instant until) {
        root.put("saving_mode", true);
        if (until != null) {
            root.put("saving_mode_until", until.toString());
        }
        return root;
    }

    public static String clientId(MapPlaceCacheEntry place) {
        return MAPBOX_ID_PREFIX + place.getProviderPlaceId();
    }

    private ObjectNode result(MapPlaceCacheEntry place, Compound compound) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("place_id", clientId(place));
        result.put("name", place.getName());
        result.put("formatted_address", place.getAddress() == null ? place.getName() : place.getAddress());
        location(result, place);
        types(result, place);
        if (place.getPhone() != null) {
            result.put("formatted_phone_number", place.getPhone());
        }
        if (place.getWebsite() != null) {
            result.put("website", place.getWebsite());
        }
        if (compound != null) {
            ObjectNode node = result.putObject("compound");
            putIfPresent(node, "commune", compound.commune());
            putIfPresent(node, "district", compound.district());
            putIfPresent(node, "province", compound.province());
        }
        return result;
    }

    private ObjectNode ok() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("status", "OK");
        return root;
    }

    private static void location(ObjectNode node, MapPlaceCacheEntry place) {
        ObjectNode location = node.putObject("geometry").putObject("location");
        location.put("lat", place.getLatitude());
        location.put("lng", place.getLongitude());
    }

    private static void types(ObjectNode node, MapPlaceCacheEntry place) {
        ArrayNode types = node.putArray("types");
        if (place.getCategories() != null) {
            Arrays.stream(place.getCategories().split(","))
                    .map(String::trim)
                    .filter(type -> !type.isEmpty())
                    .forEach(types::add);
        }
    }

    /** The address without the name in front: "133 P. Hàng Bông, Hà Nội" for "Hồ Gươm". */
    private static String secondaryText(MapPlaceCacheEntry place) {
        String address = place.getAddress();
        if (address == null || address.isBlank()) {
            return null;
        }
        String prefix = place.getName() + ", ";
        return address.startsWith(prefix) ? address.substring(prefix.length()) : address;
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }
}
