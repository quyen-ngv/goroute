package com.ds.goroute.type;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared value types used by schema v1 place attributes. */
public enum PlaceAttributeType {
    BOOLEAN(List.of(), Map.of(), false),
    LEVEL(List.of("VERY_LOW", "LOW", "MODERATE", "HIGH", "VERY_HIGH"),
            ordinalRanks("VERY_LOW", "LOW", "MODERATE", "HIGH", "VERY_HIGH"), false),
    DIFFICULTY(List.of("VERY_EASY", "EASY", "MODERATE", "HARD", "VERY_HARD"),
            ordinalRanks("VERY_EASY", "EASY", "MODERATE", "HARD", "VERY_HARD"), false),
    QUALITY(List.of("VERY_POOR", "POOR", "AVERAGE", "GOOD", "EXCELLENT"),
            ordinalRanks("VERY_POOR", "POOR", "AVERAGE", "GOOD", "EXCELLENT"), false),
    COMFORT(List.of("VERY_UNCOMFORTABLE", "UNCOMFORTABLE", "AVERAGE", "COMFORTABLE", "VERY_COMFORTABLE"),
            ordinalRanks("VERY_UNCOMFORTABLE", "UNCOMFORTABLE", "AVERAGE", "COMFORTABLE", "VERY_COMFORTABLE"), false),
    RELIABILITY(List.of("VERY_UNRELIABLE", "UNRELIABLE", "MODERATE", "RELIABLE", "VERY_RELIABLE"),
            ordinalRanks("VERY_UNRELIABLE", "UNRELIABLE", "MODERATE", "RELIABLE", "VERY_RELIABLE"), false),
    TIME_COMMITMENT(List.of("VERY_SHORT", "SHORT", "MEDIUM", "LONG", "VERY_LONG"),
            ordinalRanks("VERY_SHORT", "SHORT", "MEDIUM", "LONG", "VERY_LONG"), false),
    PRICE_LEVEL(List.of("FREE", "BUDGET", "AFFORDABLE", "MID_RANGE", "PRICEY", "PREMIUM"),
            ordinalRanks("FREE", "BUDGET", "AFFORDABLE", "MID_RANGE", "PRICEY", "PREMIUM"), false),
    SIZE(List.of("VERY_SMALL", "SMALL", "MEDIUM", "LARGE", "VERY_LARGE"),
            ordinalRanks("VERY_SMALL", "SMALL", "MEDIUM", "LARGE", "VERY_LARGE"), false),
    SPEED(List.of("VERY_SLOW", "SLOW", "MODERATE", "FAST", "VERY_FAST"),
            ordinalRanks("VERY_SLOW", "SLOW", "MODERATE", "FAST", "VERY_FAST"), false),
    AMBIENCE_TAG(List.of(
            "QUIET", "LIVELY", "COZY", "ROMANTIC", "CASUAL", "LUXURY", "LOCAL", "TRADITIONAL", "MODERN",
            "FAMILY_FRIENDLY", "WORK_FRIENDLY", "STREET_SIDE", "ROOFTOP", "GARDEN", "WATERFRONT", "SCENIC", "PARTY"),
            Map.of(), true);

    private final List<String> allowedValues;
    private final Map<String, Integer> rankByValue;
    private final boolean multiple;

    PlaceAttributeType(List<String> allowedValues, Map<String, Integer> rankByValue, boolean multiple) {
        this.allowedValues = List.copyOf(allowedValues);
        this.rankByValue = Map.copyOf(rankByValue);
        this.multiple = multiple;
    }

    public List<String> getAllowedValues() {
        return allowedValues;
    }

    public Map<String, Integer> getRankByValue() {
        return rankByValue;
    }

    public boolean isMultiple() {
        return multiple;
    }

    private static Map<String, Integer> ordinalRanks(String... values) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            result.put(values[i], i + 1);
        }
        return result;
    }
}
