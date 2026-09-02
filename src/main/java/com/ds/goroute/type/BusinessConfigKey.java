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
    /**
     * Gates the scraper worker's daily review refresh. The worker reads this before every
     * scheduled run, so switching it off here stops tomorrow's run without a redeploy.
     * Administrator-triggered refreshes and the place-activation refresh are unaffected.
     */
    PLACE_REVIEW_DAILY_REFRESH_ENABLED("PLACE_REVIEW", "DAILY_REFRESH_ENABLED", true),

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
    /**
     * Kept for the rows already seeded under this key, and read by nothing. Points do not expire
     * per-balance by age; they are halved once a year by the keys below. Left registered rather
     * than deleted so an operator who finds the row in the console sees it described here.
     *
     * @deprecated superseded by {@link #POINTS_YEAR_END_RESET_ENABLED}.
     */
    @Deprecated
    POINTS_EXPIRY_DAYS("POINTS", "EXPIRY_DAYS", 0, 0, 3650),

    /** Whether the yearly halving runs at all. Off means balances carry over untouched. */
    POINTS_YEAR_END_RESET_ENABLED("POINTS", "YEAR_END_RESET_ENABLED", true),

    /**
     * Percentage of the balance a wallet keeps at the turn of the year. 50 halves it, which is
     * the rule as decided; 100 is the same as switching the reset off.
     */
    POINTS_YEAR_END_RETAIN_PERCENT("POINTS", "YEAR_END_RETAIN_PERCENT", 50, 0, 100),

    // --- Epic 11: marketplace partner quality --------------------------------------
    /** Minutes a partner has to answer a booking request before it counts as outside the SLA. */
    PARTNER_RESPONSE_SLA_MINUTES("MARKETPLACE", "PARTNER_RESPONSE_SLA_MINUTES", 720, 5, 10080),

    // --- Epic 11: marketplace partner finance ---------------------------------------
    /**
     * Version tag frozen onto every booking together with the commission rate, so a statement can
     * always say which rule set produced a charge even after the rules change.
     */
    MARKETPLACE_COMMISSION_RULE_VERSION("MARKETPLACE", "COMMISSION_RULE_VERSION", "2026.09"),
    /** Days after a statement is issued during which a partner may still dispute one of its lines. */
    MARKETPLACE_STATEMENT_DISPUTE_WINDOW_DAYS("MARKETPLACE", "STATEMENT_DISPUTE_WINDOW_DAYS", 14, 1, 90);

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
