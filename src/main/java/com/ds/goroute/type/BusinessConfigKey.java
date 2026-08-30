package com.ds.goroute.type;

/**
 * Runtime configuration keys that are owned by code and only valued by operations.
 *
 * <p>Registering a key here is what makes it safe to expose in the admin console: the
 * label/key pair, the value type and the accepted range all live next to the feature
 * that reads them, and an out-of-range administrator value silently falls back to the
 * code default instead of changing product behaviour.
 */
public enum BusinessConfigKey {
    FREE_TRIP_MEMORY_LIMIT("TRIP_MEMORY", "FREE_TRIP_MEMORY_LIMIT", 50, 1, 1000),
    PLACE_REVIEW_REFRESH_MAX_REVIEWS("PLACE_REVIEW", "DEFAULT_REFRESH_MAX_REVIEWS", 200, 1, 200),

    // --- Epic 12: content moderation -------------------------------------------------
    MODERATION_TEXT_FILTER_ENABLED("MODERATION", "TEXT_FILTER_ENABLED", true),
    MODERATION_AI_TEXT_ENABLED("MODERATION", "AI_TEXT_ENABLED", false),
    MODERATION_AI_TEXT_MAX_LENGTH("MODERATION", "AI_TEXT_MAX_LENGTH", 4000, 200, 20000),
    MODERATION_IMAGE_ENABLED("MODERATION", "IMAGE_MODERATION_ENABLED", false),
    MODERATION_IMAGE_THRESHOLD_SEXUAL("MODERATION", "IMAGE_THRESHOLD_SEXUAL", 0.85d, 0d, 1d),
    MODERATION_IMAGE_THRESHOLD_VIOLENCE("MODERATION", "IMAGE_THRESHOLD_VIOLENCE", 0.85d, 0d, 1d),
    MODERATION_IMAGE_THRESHOLD_DRUGS_WEAPONS("MODERATION", "IMAGE_THRESHOLD_DRUGS_WEAPONS", 0.90d, 0d, 1d),
    MODERATION_IMAGE_THRESHOLD_HATE_SYMBOL("MODERATION", "IMAGE_THRESHOLD_HATE_SYMBOL", 0.80d, 0d, 1d),
    MODERATION_IMAGE_THRESHOLD_SPAM("MODERATION", "IMAGE_THRESHOLD_SPAM", 0.95d, 0d, 1d),
    MODERATION_STRICTNESS_PUBLIC("MODERATION", "STRICTNESS_PUBLIC", "FULL"),
    MODERATION_STRICTNESS_GROUP("MODERATION", "STRICTNESS_GROUP", "KEYWORD_ONLY"),
    MODERATION_STRICTNESS_DIRECT("MODERATION", "STRICTNESS_DIRECT", "REPORT_ONLY"),
    MODERATION_STRICTNESS_PRIVATE("MODERATION", "STRICTNESS_PRIVATE", "OFF"),
    MODERATION_POLICY_VERSION("MODERATION", "POLICY_VERSION", "1.0.0"),

    // --- Epic 06: check-in -----------------------------------------------------------
    CHECKIN_ENABLED("CHECKIN", "CHECKIN_ENABLED", true),
    CHECKIN_VERIFY_RADIUS_METERS("CHECKIN", "VERIFY_RADIUS_METERS", 200, 20, 5000),
    CHECKIN_MAX_ACCURACY_METERS("CHECKIN", "MAX_ACCURACY_METERS", 100, 5, 2000),
    CHECKIN_MAX_PHOTOS("CHECKIN", "MAX_PHOTOS", 6, 1, 20),
    CHECKIN_MAX_CAPTION_LENGTH("CHECKIN", "MAX_CAPTION_LENGTH", 2000, 100, 5000),
    CHECKIN_LOCATION_KEY_PRECISION("CHECKIN", "LOCATION_KEY_PRECISION", 4, 2, 6),
    CHECKIN_GALLERY_ALLOWED_FOR_FREE("CHECKIN", "GALLERY_ALLOWED_FOR_FREE", true),
    CHECKIN_GUIDE_SCREEN_ENABLED("CHECKIN", "GUIDE_SCREEN_ENABLED", true),
    CHECKIN_GUIDE_SCREEN_MAX_VIEWS("CHECKIN", "GUIDE_SCREEN_MAX_VIEWS", 3, 0, 50),
    CHECKIN_REWARD_ENABLED("CHECKIN", "REWARD_ENABLED", true),
    CHECKIN_REWARD_BASE_POINTS("CHECKIN", "REWARD_BASE_POINTS", 5, 0, 500),
    CHECKIN_REWARD_CAMERA_MULTIPLIER("CHECKIN", "REWARD_CAMERA_MULTIPLIER", 1.0d, 0d, 5d),
    CHECKIN_REWARD_GALLERY_MULTIPLIER("CHECKIN", "REWARD_GALLERY_MULTIPLIER", 0.4d, 0d, 5d),
    CHECKIN_REWARD_UNVERIFIED_MULTIPLIER("CHECKIN", "REWARD_UNVERIFIED_MULTIPLIER", 0.5d, 0d, 5d),
    CHECKIN_REWARD_DAILY_CAP("CHECKIN", "REWARD_DAILY_CAP", 50, 0, 5000),
    CHECKIN_CLUSTER_MIN_USERS("CHECKIN", "CLUSTER_MIN_USERS", 3, 1, 100),
    CHECKIN_CLUSTER_MIN_CHECKINS("CHECKIN", "CLUSTER_MIN_CHECKINS", 5, 1, 500),

    // --- Epic 04: passport -----------------------------------------------------------
    PASSPORT_ENABLED("PASSPORT", "PASSPORT_ENABLED", true),
    PASSPORT_PROVINCE_COVERAGE_THRESHOLD("PASSPORT", "PROVINCE_COVERAGE_THRESHOLD", 95, 0, 100),
    PASSPORT_TOTAL_PROVINCES("PASSPORT", "TOTAL_PROVINCES", 63, 1, 200),
    PASSPORT_LOCATION_PLACE_RADIUS_KM("PASSPORT", "LOCATION_PLACE_RADIUS_KM", 5.0d, 0.1d, 50.0d),

    // --- Epic 08: shared point wallet ------------------------------------------------
    POINTS_EXPIRY_DAYS("POINTS", "EXPIRY_DAYS", 0, 0, 3650),

    // --- Epic 07: guide marketplace --------------------------------------------------
    GUIDE_ENABLED("GUIDE", "GUIDE_ENABLED", false),
    GUIDE_PLATFORM_FEE_PERCENT("GUIDE", "PLATFORM_FEE_PERCENT", 20.0d, 0d, 100d),
    GUIDE_FEE_RULE_VERSION("GUIDE", "FEE_RULE_VERSION", "2026.08"),
    GUIDE_RESPONSE_DEADLINE_HOURS("GUIDE", "RESPONSE_DEADLINE_HOURS", 48, 1, 720),
    GUIDE_PAYOUT_HOLD_DAYS("GUIDE", "PAYOUT_HOLD_DAYS", 7, 0, 90),
    GUIDE_MIN_REVIEWS_TO_SHOW_RATING("GUIDE", "MIN_REVIEWS_TO_SHOW_RATING", 3, 1, 100),
    GUIDE_FREE_SERVICE_LIMIT("GUIDE", "FREE_SERVICE_LIMIT", 3, 1, 100),
    GUIDE_PREMIUM_MONTHLY_PRICE_VND("GUIDE", "PREMIUM_MONTHLY_PRICE_VND", 299000, 0, 100000000),
    GUIDE_PREMIUM_YEARLY_PRICE_VND("GUIDE", "PREMIUM_YEARLY_PRICE_VND", 2990000, 0, 1000000000);

    /** Value shapes the configuration layer knows how to validate. */
    public enum ValueType {
        INTEGER,
        DECIMAL,
        BOOLEAN,
        TEXT
    }

    private final String label;
    private final String key;
    private final ValueType valueType;
    private final String defaultValue;
    private final double minimumValue;
    private final double maximumValue;

    BusinessConfigKey(String label, String key, int defaultValue, int minimumValue, int maximumValue) {
        this(label, key, ValueType.INTEGER, String.valueOf(defaultValue), minimumValue, maximumValue);
    }

    BusinessConfigKey(String label, String key, double defaultValue, double minimumValue, double maximumValue) {
        this(label, key, ValueType.DECIMAL, String.valueOf(defaultValue), minimumValue, maximumValue);
    }

    BusinessConfigKey(String label, String key, boolean defaultValue) {
        this(label, key, ValueType.BOOLEAN, String.valueOf(defaultValue), 0, 0);
    }

    BusinessConfigKey(String label, String key, String defaultValue) {
        this(label, key, ValueType.TEXT, defaultValue, 0, 0);
    }

    BusinessConfigKey(String label, String key, ValueType valueType, String defaultValue,
                      double minimumValue, double maximumValue) {
        this.label = label;
        this.key = key;
        this.valueType = valueType;
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

    public ValueType valueType() {
        return valueType;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public int defaultInt() {
        return Integer.parseInt(defaultValue);
    }

    public double defaultDecimal() {
        return Double.parseDouble(defaultValue);
    }

    public boolean defaultBoolean() {
        return Boolean.parseBoolean(defaultValue);
    }

    public boolean accepts(double value) {
        return value >= minimumValue && value <= maximumValue;
    }
}
