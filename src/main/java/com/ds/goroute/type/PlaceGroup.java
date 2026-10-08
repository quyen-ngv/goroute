package com.ds.goroute.type;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The coarse tab a place lives under. Finer kinds (beach, temple, cafe, halal…) are
 * {@link PlaceSubType}s. Until V219 there were also CULTURE_AND_HERITAGE, NATURE_AND_OUTDOORS,
 * SHOPPING_AND_MARKET and OTHER; old app builds, the scraper and stored AI-trip requests still
 * send them, so {@link #fromCode} reads them as {@link #ATTRACTIONS}.
 */
public enum PlaceGroup {
    FOOD_AND_DRINK,
    ATTRACTIONS,
    ACCOMMODATION;

    private static final List<String> LEGACY_ATTRACTION_CODES = List.of(
            "CULTURE_AND_HERITAGE", "NATURE_AND_OUTDOORS", "SHOPPING_AND_MARKET", "OTHER");

    /**
     * The group a code names, legacy codes included; null for a blank or unknown code.
     * Also Jackson's reader, so a request carrying a legacy code still binds.
     */
    @JsonCreator
    public static PlaceGroup fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (LEGACY_ATTRACTION_CODES.contains(normalized)) {
            return ATTRACTIONS;
        }
        if ("FOOD".equals(normalized)) {
            return FOOD_AND_DRINK; // the AI worker's short name
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /**
     * Distinct current names of a code filter, legacy codes folded in; null when nothing is left.
     * An unknown code is kept as written so it still matches nothing, rather than silently
     * widening the filter to every group.
     */
    public static List<String> normalizeCodes(Collection<String> codes) {
        if (codes == null) {
            return null;
        }
        List<String> names = codes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .map(code -> {
                    PlaceGroup group = fromCode(code);
                    return group != null ? group.name() : code.toUpperCase(Locale.ROOT);
                })
                .distinct()
                .toList();
        return names.isEmpty() ? null : names;
    }
}
