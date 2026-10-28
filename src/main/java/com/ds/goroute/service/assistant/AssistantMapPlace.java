package com.ds.goroute.service.assistant;

import com.ds.goroute.dto.request.AssistantReplyRequest;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * A place search_places found on the map (Goong or Mapbox) and not in the catalogue. It is never
 * written to {@code places}: a card shows it, and adding it to a trip makes a plain activity with
 * its name, address and coordinates ({@code activities.mapbox_id} too when Mapbox found it; nothing
 * reads a Goong id, so none is kept).
 */
record AssistantMapPlace(String provider, String providerPlaceId, String name, String address,
                         BigDecimal lat, BigDecimal lng) {

    static final Set<String> PROVIDERS = Set.of("GOONG", "MAPBOX");
    static final String MAPBOX = "MAPBOX";
    static final int MAX_ID = 255;
    static final int MAX_NAME = 200;
    static final int MAX_ADDRESS = 300;
    private static final BigDecimal LAT_LIMIT = BigDecimal.valueOf(90);
    private static final BigDecimal LNG_LIMIT = BigDecimal.valueOf(180);

    /**
     * The ref when every field is within bounds and {@code allowed} lets its name (and address,
     * when it has one) through; empty otherwise, and the card, proposal or stop is dropped.
     *
     * @param allowed the text filter: the text to keep, or null when it is blocked or blank
     */
    static Optional<AssistantMapPlace> of(AssistantReplyRequest.MapPlaceRef ref, UnaryOperator<String> allowed) {
        if (ref == null) return Optional.empty();
        String provider = provider(ref.getProvider());
        String id = trimmed(ref.getProviderPlaceId());
        String rawName = trimmed(ref.getName());
        String rawAddress = trimmed(ref.getAddress());
        if (!hasLocation(provider, id, ref.getLat(), ref.getLng())) return Optional.empty();
        if (rawName == null || rawName.length() > MAX_NAME) return Optional.empty();
        if (rawAddress != null && rawAddress.length() > MAX_ADDRESS) return Optional.empty();
        String name = allowed.apply(rawName);
        String address = rawAddress == null ? null : allowed.apply(rawAddress);
        if (name == null || (rawAddress != null && address == null)) return Optional.empty();
        return Optional.of(new AssistantMapPlace(provider, id, name, address, ref.getLat(), ref.getLng()));
    }

    /** The checks a plan stop's map fields need: the stop keeps its own name and address. */
    static boolean hasLocation(String provider, String providerPlaceId, BigDecimal lat, BigDecimal lng) {
        return provider != null && PROVIDERS.contains(provider)
                && providerPlaceId != null && !providerPlaceId.isEmpty() && providerPlaceId.length() <= MAX_ID
                && lat != null && lng != null
                && lat.abs().compareTo(LAT_LIMIT) <= 0 && lng.abs().compareTo(LNG_LIMIT) <= 0;
    }

    /** GOONG or MAPBOX in upper case, or null. */
    static String provider(String raw) {
        String value = trimmed(raw);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    /** Trims; blank is null. The filter for text that has already been through the chat filter. */
    static String trimmed(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    boolean isMapbox() {
        return MAPBOX.equals(provider);
    }

    /** The answer's card. */
    Map<String, Object> block(String note) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("kind", "MAP_PLACE");
        block.put("provider", provider);
        block.put("providerPlaceId", providerPlaceId);
        block.put("name", name);
        if (address != null) block.put("address", address);
        block.put("lat", lat);
        block.put("lng", lng);
        if (note != null && !note.isEmpty()) block.put("note", note);
        return block;
    }

    /** What an ADD or REPLACE proposal keeps of it; applying reads the same keys back. */
    void putInto(Map<String, Object> payload) {
        payload.put("placeName", name);
        if (address != null) payload.put("address", address);
        payload.put("lat", lat.toPlainString());
        payload.put("lng", lng.toPlainString());
        payload.put("mapProvider", provider);
        payload.put("mapPlaceId", providerPlaceId);
    }

    /** A copy carrying the filtered name and address in place of the worker's. */
    AssistantReplyRequest.MapPlaceRef toRef() {
        AssistantReplyRequest.MapPlaceRef ref = new AssistantReplyRequest.MapPlaceRef();
        ref.setProvider(provider);
        ref.setProviderPlaceId(providerPlaceId);
        ref.setName(name);
        ref.setAddress(address);
        ref.setLat(lat);
        ref.setLng(lng);
        return ref;
    }
}
