package com.ds.goroute.type;

public enum BusinessConfigKey {
    FREE_TRIP_MEMORY_LIMIT(
            "TRIP_MEMORY",
            "FREE_TRIP_MEMORY_LIMIT",
            50,
            1,
            1000),
    PLACE_REVIEW_REFRESH_MAX_REVIEWS(
            "PLACE_REVIEW",
            "DEFAULT_REFRESH_MAX_REVIEWS",
            200,
            1,
            200);

    private final String label;
    private final String key;
    private final int defaultValue;
    private final int minimumValue;
    private final int maximumValue;

    BusinessConfigKey(
            String label,
            String key,
            int defaultValue,
            int minimumValue,
            int maximumValue) {
        this.label = label;
        this.key = key;
        this.defaultValue = defaultValue;
        this.minimumValue = minimumValue;
        this.maximumValue = maximumValue;
    }

    public String label() {
        return label;
    }

    public String key() {
        return key;
    }

    public int defaultValue() {
        return defaultValue;
    }

    public boolean accepts(int value) {
        return value >= minimumValue && value <= maximumValue;
    }
}
