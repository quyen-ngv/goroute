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
    TRIP_FREE_CREATION_QUOTA("TRIP", "FREE_TRIP_QUOTA", 3, 0, 1000),
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
    MODERATION_STRICTNESS_CHAT("MODERATION", "STRICTNESS_CHAT", "KEYWORD_BLOCK_ONLY"),
    MODERATION_STRICTNESS_PRIVATE("MODERATION", "STRICTNESS_PRIVATE", "OFF"),
    MODERATION_POLICY_VERSION("MODERATION", "POLICY_VERSION", "1.0.0"),

    // --- Epic 03: AI trip generation -----------------------------------------------
    AI_TRIP_FREE_QUOTA("AI_TRIP_QUOTA", "FREE_TRIP_QUOTA", 3, 0, 1000),
    AI_TRIP_PRO_QUOTA("AI_TRIP_QUOTA", "PRO_TRIP_QUOTA", 10, 0, 1000),

    // --- Epic 09: social-location extraction ---------------------------------------
    SOCIAL_LOCATION_DAILY_JOB_LIMIT("SOCIAL_LOCATION", "DAILY_JOB_LIMIT_DEFAULT", 5, 0, 1000),

    // --- Social video itinerary defaults --------------------------------------------
    SOCIAL_START_OFFSET_DAYS("AI_TRIP", "SOCIAL_START_OFFSET_DAYS", 5, 0, 30),
    LOCATION_IMAGE_DEFAULT_URL("LOCATION_IMAGE", "DEFAULT_IMAGE_URL",
            "https://images.unsplash.com/photo-1488646953014-85cb44e25828"),

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
    /**
     * Applied when the location was proven only at ward level. Sits between the camera
     * and unverified multipliers on purpose: being somewhere in a ward is easier to
     * achieve than being at the place, and paying both the same would empty the PLACE badge.
     */
    CHECKIN_REWARD_WARD_VERIFIED_MULTIPLIER("CHECKIN", "REWARD_WARD_VERIFIED_MULTIPLIER", 0.7d, 0d, 5d),
    CHECKIN_REWARD_DAILY_CAP("CHECKIN", "REWARD_DAILY_CAP", 50, 0, 5000),
    CHECKIN_CLUSTER_MIN_USERS("CHECKIN", "CLUSTER_MIN_USERS", 3, 1, 100),
    CHECKIN_CLUSTER_MIN_CHECKINS("CHECKIN", "CLUSTER_MIN_CHECKINS", 5, 1, 500),

    // --- Epic 04: passport -----------------------------------------------------------
    PASSPORT_ENABLED("PASSPORT", "PASSPORT_ENABLED", true),
    PASSPORT_PROVINCE_COVERAGE_THRESHOLD("PASSPORT", "PROVINCE_COVERAGE_THRESHOLD", 95, 0, 100),
    /** 34 since the 2025 merger (NQ 202/2025/QH15); the geo backfill moves the seeded 63 to it. */
    PASSPORT_TOTAL_PROVINCES("PASSPORT", "TOTAL_PROVINCES", 34, 1, 200),
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

    // --- Epic 05: expenses ----------------------------------------------------------
    /** Reminders one payer may send the same person per day (Asia/Ho_Chi_Minh); each covers all picked splits. */
    EXPENSE_PAYMENT_REMINDER_DAILY_MAX("EXPENSE", "PAYMENT_REMINDER_DAILY_MAX", 2, 1, 20),

    // --- Epic 11: marketplace partner finance ---------------------------------------
    /**
     * Version tag frozen onto every booking together with the commission rate, so a statement can
     * always say which rule set produced a charge even after the rules change.
     */
    MARKETPLACE_COMMISSION_RULE_VERSION("MARKETPLACE", "COMMISSION_RULE_VERSION", "2026.09"),
    /** Days after a statement is issued during which a partner may still dispute one of its lines. */
    MARKETPLACE_STATEMENT_DISPUTE_WINDOW_DAYS("MARKETPLACE", "STATEMENT_DISPUTE_WINDOW_DAYS", 14, 1, 90),

    // --- Epic 15: partner onboarding ------------------------------------------------
    /**
     * Whether the listing wizard is offered at all. Reported to both clients through
     * {@code GET /partner-onboarding/me}, so turning it off hides the entry point without a
     * release. Existing drafts stay readable; only starting a new one is refused.
     */
    PARTNER_ONBOARDING_ENABLED("PARTNER_ONBOARDING", "ENABLED", false),
    /** Unfinished drafts one account may hold. Keeps an abandoned-draft loop from filling the table. */
    PARTNER_ONBOARDING_MAX_DRAFTS("PARTNER_ONBOARDING", "MAX_ACTIVE_DRAFTS", 5, 1, 20),
    /**
     * How far ahead the wizard generates departure slots for an experience or a service. Long
     * enough to be sellable on day one, short enough that a mistake is not 700 rows to undo.
     */
    PARTNER_ONBOARDING_SLOT_HORIZON_DAYS("PARTNER_ONBOARDING", "SLOT_HORIZON_DAYS", 60, 7, 180),
    /** How far ahead the weekend uplift chosen in the wizard is written onto the rate calendar. */
    PARTNER_ONBOARDING_WEEKEND_HORIZON_DAYS("PARTNER_ONBOARDING", "WEEKEND_HORIZON_DAYS", 365, 30, 730),
    /** Whether the partner workspace is offered inside the mobile app. */
    PARTNER_APP_ENABLED("PARTNER_APP", "ENABLED", false),

    // --- Epic 11: marketplace chat ---------------------------------------------------
    /**
     * Person-to-person conversations. Off is the emergency brake; booking chat is unaffected.
     *
     * <p>How often a conversation may notify is deliberately not a key: message notifications
     * reuse the same thirty-minute unread window that groups likes and comments, so the product
     * has one answer to "how often will this buzz" rather than one per feature.
     */
    CHAT_DIRECT_ENABLED("CHAT", "DIRECT_ENABLED", true),

    // --- Chat assistant (Pengoo) ------------------------------------------------------
    /** Everyone may ask Pengoo; beta users (USER.BETA_USER) may ask while this is off. */
    ASSISTANT_ENABLED("ASSISTANT", "ENABLED", false),
    /** Questions per local (Vietnam) day for a FREE account. A failed answer is not counted. */
    ASSISTANT_FREE_DAILY_QUESTIONS("ASSISTANT", "FREE_DAILY_QUESTIONS", 5, 0, 1000),
    /** Questions per local (Vietnam) day for a PRO account. */
    ASSISTANT_PRO_DAILY_QUESTIONS("ASSISTANT", "PRO_DAILY_QUESTIONS", 50, 0, 10000),
    /**
     * Whether prompts and answers are stored in ai_api_calls. On only during the beta: chat
     * text is otherwise private, and the log is readable from the console.
     */
    ASSISTANT_LOG_PAYLOADS("ASSISTANT", "LOG_PAYLOADS", true),
    /** Recent messages sent with each question as conversation context. */
    ASSISTANT_HISTORY_MESSAGES("ASSISTANT", "HISTORY_MESSAGES", 20, 0, 50),
    /** Pengoo greets a new trip's chat with suggestion chips (no model call, not counted). */
    ASSISTANT_WELCOME_ENABLED("ASSISTANT", "WELCOME_ENABLED", true),
    /** A turn still running after this many seconds is failed and the asker is told. */
    ASSISTANT_TURN_TIMEOUT_SECONDS("ASSISTANT", "TURN_TIMEOUT_SECONDS", 150, 30, 900),

    // --- Media upload -----------------------------------------------------------------
    /**
     * Maximum number of files allowed in a single batch upload request.
     * Higher values allow faster bulk uploads but may strain server resources.
     */
    UPLOAD_MAX_BATCH_FILES("MEDIA_UPLOAD", "MAX_BATCH_FILES", 30, 1, 100),

    // --- Quest platform (§7.7) -------------------------------------------------------
    // Every threshold the quest feature reads lives here so an out-of-range admin value
    // silently falls back to the code default instead of changing product behaviour. The
    // feature ships dark: QUEST_ENABLED is false until the switch is thrown per phase.
    QUEST_ENABLED("QUEST", "ENABLED", false),
    QUEST_MIN_CHECKPOINTS("QUEST", "MIN_CHECKPOINTS", 4, 2, 50),
    QUEST_MAX_CHECKPOINTS("QUEST", "MAX_CHECKPOINTS", 15, 2, 50),
    QUEST_UNLOCK_RADIUS_METERS("QUEST", "UNLOCK_RADIUS_METERS", 40, 25, 150),
    QUEST_MIN_LEG_METERS("QUEST", "MIN_LEG_METERS", 80, 0, 100000),
    QUEST_MAX_LEG_METERS("QUEST", "MAX_LEG_METERS", 1500, 0, 100000),
    QUEST_FIELD_TEST_COMPLETIONS("QUEST", "FIELD_TEST_COMPLETIONS", 5, 1, 50),
    QUEST_CAPTURE_MAX_ACCURACY_METERS("QUEST", "CAPTURE_MAX_ACCURACY_METERS", 30, 5, 100),
    QUEST_CHECKPOINT_DROPOFF_ALERT_PCT("QUEST", "CHECKPOINT_DROPOFF_ALERT_PCT", 40, 10, 90),
    QUEST_AUTOPAUSE_SEVERE_REPORTS_24H("QUEST", "AUTOPAUSE_SEVERE_REPORTS_24H", 3, 1, 20),
    QUEST_MAX_PENDING_PER_CREATOR("QUEST", "MAX_PENDING_PER_CREATOR", 1, 1, 10),
    QUEST_MAX_PUBLISHED_NEW_CREATOR("QUEST", "MAX_PUBLISHED_NEW_CREATOR", 3, 1, 50),
    QUEST_SUBMIT_COOLDOWN_MINUTES("QUEST", "SUBMIT_COOLDOWN_MINUTES", 30, 0, 1440),
    QUEST_DENIED_STREAK_BLOCK("QUEST", "DENIED_STREAK_BLOCK", 3, 1, 20),
    QUEST_DENIED_BLOCK_DAYS("QUEST", "DENIED_BLOCK_DAYS", 7, 1, 90),
    QUEST_CREATOR_REVENUE_SHARE_PCT("QUEST", "CREATOR_REVENUE_SHARE_PCT", 70, 0, 90),
    QUEST_MAX_GUESS_ATTEMPTS("QUEST", "MAX_GUESS_ATTEMPTS", 5, 1, 20),
    QUEST_GROUP_MAX_PLAYERS("QUEST", "GROUP_MAX_PLAYERS", 6, 2, 20),
    QUEST_STALE_REVIEW_MONTHS("QUEST", "STALE_REVIEW_MONTHS", 6, 1, 36),
    QUEST_ARRIVAL_STABLE_SAMPLES("QUEST", "ARRIVAL_STABLE_SAMPLES", 1, 1, 10),
    QUEST_ARRIVAL_CLIENT_INTERVAL_SECONDS("QUEST", "ARRIVAL_CLIENT_INTERVAL_SECONDS", 5, 2, 120),
    QUEST_ARRIVAL_MIN_SAMPLE_GAP_SECONDS("QUEST", "ARRIVAL_MIN_SAMPLE_GAP_SECONDS", 3, 1, 60),
    QUEST_ARRIVAL_SAMPLE_MAX_AGE_SECONDS("QUEST", "ARRIVAL_SAMPLE_MAX_AGE_SECONDS", 60, 10, 600),
    QUEST_AUTOPAUSE_DROPOFF_PCT("QUEST", "AUTOPAUSE_DROPOFF_PCT", 60, 10, 100),
    QUEST_AUTOPAUSE_MIN_RUNS("QUEST", "AUTOPAUSE_MIN_RUNS", 20, 5, 500),
    QUEST_AUTOPAUSE_WINDOW_HOURS("QUEST", "AUTOPAUSE_WINDOW_HOURS", 72, 1, 720),
    QUEST_AUTOPAUSE_GPS_OVERRIDE_PCT("QUEST", "AUTOPAUSE_GPS_OVERRIDE_PCT", 50, 10, 100),
    QUEST_GROUP_PRESENCE_PCT("QUEST", "GROUP_PRESENCE_PCT", 70, 0, 100),
    QUEST_INVITE_TTL_HOURS("QUEST", "INVITE_TTL_HOURS", 12, 1, 168),
    QUEST_RUN_IDLE_HOURS("QUEST", "RUN_IDLE_HOURS", 48, 1, 720),
    QUEST_RUN_EXPIRY_DAYS("QUEST", "RUN_EXPIRY_DAYS", 14, 1, 365),
    QUEST_ARCHIVE_RESTORE_DAYS("QUEST", "ARCHIVE_RESTORE_DAYS", 30, 1, 365),
    QUEST_EARNING_SETTLE_DAYS("QUEST", "EARNING_SETTLE_DAYS", 14, 0, 90),
    QUEST_LOCATION_SAMPLE_RETENTION_DAYS("QUEST", "LOCATION_SAMPLE_RETENTION_DAYS", 30, 7, 365),
    QUEST_REVIEW_QUEUE_AGING_DAYS("QUEST", "REVIEW_QUEUE_AGING_DAYS", 3, 1, 30),
    QUEST_REVIEW_CLAIM_TIMEOUT_MINUTES("QUEST", "REVIEW_CLAIM_TIMEOUT_MINUTES", 60, 5, 1440),
    QUEST_SKIP_AFTER_MINUTES("QUEST", "SKIP_AFTER_MINUTES", 15, 1, 120),
    QUEST_GPS_OVERRIDE_AFTER_MINUTES("QUEST", "GPS_OVERRIDE_AFTER_MINUTES", 3, 1, 60),
    QUEST_HINT_TIER2_STARS("QUEST", "HINT_TIER2_STARS", 2, 0, 10000),
    QUEST_HINT_TIER3_STARS("QUEST", "HINT_TIER3_STARS", 5, 0, 10000),
    QUEST_REVEAL_STARS("QUEST", "REVEAL_STARS", 15, 0, 10000),
    QUEST_MAX_QUESTIONS_PER_CHECKPOINT("QUEST", "MAX_QUESTIONS_PER_CHECKPOINT", 3, 1, 10),
    QUEST_MAX_BONUS_QUESTIONS("QUEST", "MAX_BONUS_QUESTIONS", 2, 0, 5),
    QUEST_BONUS_QUESTION_STARS("QUEST", "BONUS_QUESTION_STARS", 3, 0, 50),
    QUEST_TEXT_ANSWER_MAX_WORDS("QUEST", "TEXT_ANSWER_MAX_WORDS", 4, 1, 20),
    QUEST_CHOICE_MIN_OPTIONS("QUEST", "CHOICE_MIN_OPTIONS", 3, 2, 10),
    QUEST_CHOICE_MAX_OPTIONS("QUEST", "CHOICE_MAX_OPTIONS", 5, 2, 10),
    QUEST_DUPLICATE_OVERLAP_PCT("QUEST", "DUPLICATE_OVERLAP_PCT", 60, 0, 100),
    QUEST_DUPLICATE_RADIUS_METERS("QUEST", "DUPLICATE_RADIUS_METERS", 2000, 0, 100000),
    QUEST_GEOFENCE_EXTRA_RADIUS_METERS("QUEST", "GEOFENCE_EXTRA_RADIUS_METERS", 30, 0, 200),
    QUEST_GEOFENCE_ENABLED("QUEST", "GEOFENCE_ENABLED", true),
    QUEST_GEOFENCE_REGIONS_PER_RUN("QUEST", "GEOFENCE_REGIONS_PER_RUN", 2, 1, 5),
    QUEST_PLAYERS_LIVE_WINDOW_MINUTES("QUEST", "PLAYERS_LIVE_WINDOW_MINUTES", 30, 5, 240),
    QUEST_PLAYER_TIMELINE_RETENTION_DAYS("QUEST", "PLAYER_TIMELINE_RETENTION_DAYS", 90, 7, 365),
    QUEST_EDITS_PER_WINDOW("QUEST", "EDITS_PER_WINDOW", 5, 1, 50),
    QUEST_EDIT_WINDOW_DAYS("QUEST", "EDIT_WINDOW_DAYS", 7, 1, 90),
    QUEST_CHANGE_REVIEW_ESCALATE_HOURS("QUEST", "CHANGE_REVIEW_ESCALATE_HOURS", 72, 1, 720),
    QUEST_TIP_MIN_STARS("QUEST", "TIP_MIN_STARS", 5, 0, 100000),
    QUEST_TIP_MAX_STARS("QUEST", "TIP_MAX_STARS", 500, 0, 100000),
    QUEST_TIP_PLATFORM_CUT_PCT("QUEST", "TIP_PLATFORM_CUT_PCT", 0, 0, 50),
    QUEST_PRICE_MAX_STARS("QUEST", "PRICE_MAX_STARS", 200, 0, 2000),
    // §3.14 dynamic checkpoints: finding clues, storytelling points, hot/cold and recordings.
    QUEST_CLUE_MAX_STARS("QUEST", "CLUE_MAX_STARS", 50, 0, 1000),
    QUEST_MAX_STOPS_PER_CHECKPOINT("QUEST", "MAX_STOPS_PER_CHECKPOINT", 20, 1, 50),
    QUEST_STOP_MAX_DISTANCE_M("QUEST", "STOP_MAX_DISTANCE_M", 1000, 50, 5000),
    QUEST_PROXIMITY_MIN_INTERVAL_SECONDS("QUEST", "PROXIMITY_MIN_INTERVAL_SECONDS", 10, 1, 300),
    QUEST_AUDIO_MAX_SECONDS("QUEST", "AUDIO_MAX_SECONDS", 300, 10, 1800),
    // §3.15 AR objects. Creating one is open to beta testers only until AR_ENABLED is thrown;
    // playing one is open to everyone. The interact radius is how near the object a tap counts.
    QUEST_AR_ENABLED("QUEST", "AR_ENABLED", false),
    QUEST_AR_INTERACT_RADIUS_METERS("QUEST", "AR_INTERACT_RADIUS_METERS", 40, 10, 150),
    // How many 3D objects one creator may upload for their own quests (each up to ~20 MB stored).
    QUEST_AR_CREATOR_MAX_OBJECTS("QUEST", "AR_CREATOR_MAX_OBJECTS", 20, 0, 200),
    // The largest GLB or USDZ the server takes, in MB. Players download the model with the quest,
    // so a large one costs every player that much data; the upload also warns above 5 MB.
    QUEST_AR_MAX_MODEL_MB("QUEST", "AR_MAX_MODEL_MB", 50, 1, 200),
    // The most triangles a GLB may have. Detail beyond what the phone shows costs frame time, but
    // the product favours fidelity; the upload warns above ArModelInspector.WARN_TRIANGLES.
    QUEST_AR_MAX_TRIANGLES("QUEST", "AR_MAX_TRIANGLES", 1_000_000, 1_000, 5_000_000),

    // --- Epic 16: travel tracker (.docs/designs/TRAVEL_TRACKER_PLAN.md) ----------------
    // The app reads these through GET /v1/api/trips/{tripId}/track/context, not PUBLIC_CONFIG:
    // they only mean something once the user is inside a trip.
    TRACKER_ENABLED("TRACKER", "ENABLED", false),
    // Days before the first and after the last day of a trip that still accept points (TR-02).
    TRACKER_WINDOW_PAD_DAYS("TRACKER", "WINDOW_PAD_DAYS", 1, 0, 3),
    // A point whose reported accuracy is worse than this (m) is dropped, per recording mode.
    TRACKER_MAX_ACCURACY_HIGH_M("TRACKER", "MAX_ACCURACY_HIGH_M", 30, 5, 500),
    TRACKER_MAX_ACCURACY_BALANCED_M("TRACKER", "MAX_ACCURACY_BALANCED_M", 60, 5, 500),
    TRACKER_MAX_ACCURACY_SAVER_M("TRACKER", "MAX_ACCURACY_SAVER_M", 150, 5, 500),
    // Distance filter the phone asks the OS for, per recording mode (m).
    TRACKER_DISTANCE_FILTER_HIGH_M("TRACKER", "DISTANCE_FILTER_HIGH_M", 10, 1, 500),
    TRACKER_DISTANCE_FILTER_BALANCED_M("TRACKER", "DISTANCE_FILTER_BALANCED_M", 25, 1, 500),
    TRACKER_DISTANCE_FILTER_SAVER_M("TRACKER", "DISTANCE_FILTER_SAVER_M", 100, 1, 500),
    // A short hop faster than this (km/h) is a GPS jump and is ignored when building the line.
    TRACKER_MAX_SPEED_KMH("TRACKER", "MAX_SPEED_KMH", 300, 50, 1200),
    // No signal for longer than GAP_MINUTES and further than GAP_DISTANCE_M: dashed gap (TR-11).
    TRACKER_GAP_MINUTES("TRACKER", "GAP_MINUTES", 10, 1, 240),
    TRACKER_GAP_DISTANCE_M("TRACKER", "GAP_DISTANCE_M", 1000, 100, 100_000),
    // A hop at least this long and this fast is a flight: arc, counted apart (TR-12).
    TRACKER_FLIGHT_MIN_KM("TRACKER", "FLIGHT_MIN_KM", 200, 50, 2000),
    TRACKER_FLIGHT_MIN_KMH("TRACKER", "FLIGHT_MIN_KMH", 300, 100, 1200),
    // Standing still within this radius for this long auto-pauses recording (TR-08).
    TRACKER_STATIONARY_RADIUS_M("TRACKER", "STATIONARY_RADIUS_M", 30, 5, 200),
    TRACKER_STATIONARY_MINUTES("TRACKER", "STATIONARY_MINUTES", 3, 1, 30),
    // Within this radius of a planned activity for this long counts as having arrived (TR-15).
    TRACKER_ARRIVAL_RADIUS_M("TRACKER", "ARRIVAL_RADIUS_M", 100, 20, 500),
    TRACKER_ARRIVAL_MINUTES("TRACKER", "ARRIVAL_MINUTES", 3, 1, 60),
    // Check-in nudges: spacing, daily cap and quiet hours in the trip's local time (TR-18).
    TRACKER_NUDGE_COOLDOWN_MINUTES("TRACKER", "NUDGE_COOLDOWN_MINUTES", 20, 0, 240),
    TRACKER_NUDGE_DAILY_MAX("TRACKER", "NUDGE_DAILY_MAX", 6, 0, 50),
    TRACKER_QUIET_START_HOUR("TRACKER", "QUIET_START_HOUR", 22, 0, 23),
    TRACKER_QUIET_END_HOUR("TRACKER", "QUIET_END_HOUR", 7, 0, 23),
    // Battery levels (%) that switch to the saver mode and that end the session (TR-09).
    TRACKER_LOW_BATTERY_PCT("TRACKER", "LOW_BATTERY_PCT", 15, 5, 50),
    TRACKER_STOP_BATTERY_PCT("TRACKER", "STOP_BATTERY_PCT", 5, 1, 30),
    // Raw points are kept this many days after the trip's last day; day lines stay (TR-24).
    TRACKER_RAW_RETENTION_DAYS("TRACKER", "RAW_RETENTION_DAYS", 90, 7, 365),
    // Most points one upload may carry.
    TRACKER_CHUNK_MAX_POINTS("TRACKER", "CHUNK_MAX_POINTS", 500, 50, 500),
    // Off-plan stop (P2, TR-16): within this radius (m) for this long (minutes), away from
    // every planned activity of the day.
    TRACKER_OFFPLAN_RADIUS_M("TRACKER", "OFFPLAN_RADIUS_M", 100, 20, 500),
    TRACKER_OFFPLAN_MIN_MINUTES("TRACKER", "OFFPLAN_MIN_MINUTES", 10, 1, 120),
    // A catalogue place this close (m) to an off-plan stop names it; otherwise Goong does.
    TRACKER_OFFPLAN_NAME_RADIUS_M("TRACKER", "OFFPLAN_NAME_RADIUS_M", 60, 10, 500),

    // --- Epic 17: local events --------------------------------------------------------
    // Event data is public, so reads are never gated; the app shows the Home "Sự kiện" tab when
    // this is on (read through PUBLIC_CONFIG) or the user is a beta user. Joining an event and its
    // group chat are gated on the server by the same flag (LocalEventAttendanceService). On for
    // everyone since V224.
    EVENT_ENABLED("EVENT", "ENABLED", true),

    // --- Epic 18: meetups ("Hoạt động") -----------------------------------------------
    // The flag gates every meetup user endpoint on the server (beta users always pass) and is
    // folded into GET /v1/api/meetups/context (on for everyone since V224); the other keys are
    // the caps and time windows.
    MEETUP_ENABLED("MEETUP", "ENABLED", true),
    MEETUP_MAX_ACTIVE_PER_HOST("MEETUP", "MAX_ACTIVE_PER_HOST", 2, 1, 10),
    MEETUP_MAX_SUBMISSIONS_PER_DAY("MEETUP", "MAX_SUBMISSIONS_PER_DAY", 5, 1, 50),
    MEETUP_MAX_DAYS_AHEAD("MEETUP", "MAX_DAYS_AHEAD", 3, 0, 14),
    MEETUP_EXACT_MIN_LEAD_MINUTES("MEETUP", "EXACT_MIN_LEAD_MINUTES", 30, 0, 720),
    MEETUP_EXACT_VISIBLE_HOURS("MEETUP", "EXACT_VISIBLE_HOURS", 3, 1, 12),
    MEETUP_MAX_OPEN_REQUESTS_PER_USER("MEETUP", "MAX_OPEN_REQUESTS_PER_USER", 5, 1, 50),
    MEETUP_MAX_RESUBMITS("MEETUP", "MAX_RESUBMITS", 2, 0, 10),

    // --- Epic 06: per-user limits on the app's Goong search proxy ---------------------
    // Every autocomplete and place-detail call through /v1/api/goong is a paid Goong
    // request. Server-side lookups (AI trip, tracker naming) are not counted here.
    MAP_SEARCH_RATE_LIMIT_ENABLED("MAP_SEARCH", "RATE_LIMIT_ENABLED", true),
    /** Operators fill place addresses from the console all day; they are staff, not abuse. */
    MAP_SEARCH_EXEMPT_ADMINS("MAP_SEARCH", "EXEMPT_ADMINS", true),
    MAP_SEARCH_AUTOCOMPLETE_PER_MINUTE("MAP_SEARCH", "AUTOCOMPLETE_PER_MINUTE", 30, 1, 600),
    MAP_SEARCH_AUTOCOMPLETE_PER_DAY("MAP_SEARCH", "AUTOCOMPLETE_PER_DAY", 300, 1, 100000),
    MAP_SEARCH_PLACE_DETAIL_PER_MINUTE("MAP_SEARCH", "PLACE_DETAIL_PER_MINUTE", 30, 1, 600),
    MAP_SEARCH_PLACE_DETAIL_PER_DAY("MAP_SEARCH", "PLACE_DETAIL_PER_DAY", 200, 1, 100000),
    // Reverse/forward geocoding (/v1/api/goong/geocode) and road routes (/v1/api/routes/day).
    MAP_SEARCH_GEOCODE_PER_MINUTE("MAP_SEARCH", "GEOCODE_PER_MINUTE", 20, 1, 600),
    MAP_SEARCH_GEOCODE_PER_DAY("MAP_SEARCH", "GEOCODE_PER_DAY", 200, 1, 100000),
    MAP_SEARCH_ROUTE_PER_MINUTE("MAP_SEARCH", "ROUTE_PER_MINUTE", 30, 1, 600),
    MAP_SEARCH_ROUTE_PER_DAY("MAP_SEARCH", "ROUTE_PER_DAY", 300, 1, 100000),
    /** Days of per-user daily counters kept for reporting before the nightly purge. */
    MAP_SEARCH_USAGE_RETENTION_DAYS("MAP_SEARCH", "USAGE_RETENTION_DAYS", 90, 7, 730),

    // --- Epic 06: which map provider answers search, geocode and routes ----------------
    // Values: GOONG or MAPBOX. Beta users (USER/BETA_USER) read BETA_PROVIDER, everybody else
    // USER_PROVIDER. A provider without a key is skipped (MAPBOX -> GOONG -> catalogue only).
    MAP_PROVIDER_USER("MAP_PROVIDER", "USER_PROVIDER", "GOONG"),
    MAP_PROVIDER_BETA("MAP_PROVIDER", "BETA_PROVIDER", "MAPBOX"),
    /** Mapbox failing (5xx, 429, timeout) retries with Goong; Goong only knows Vietnam. */
    MAP_PROVIDER_FALLBACK_TO_GOONG("MAP_PROVIDER", "FALLBACK_TO_GOONG", true),

    // --- Epic 06: ceiling on paid Mapbox calls for the whole system ---------------------
    // Counted per calendar month (UTC) and per day. At the cap the server stops calling Mapbox
    // and answers from its caches and the catalogue ("saving mode") until the period ends.
    MAP_BUDGET_ENABLED("MAP_BUDGET", "ENABLED", true),
    /** Search Box requests (forward, reverse, retrieve). Free tier: 50,000 a month. */
    MAP_BUDGET_SEARCH_MONTHLY_CAP("MAP_BUDGET", "SEARCH_MONTHLY_CAP", 50000, 0, 100000000),
    /** Geocoding v6 requests (city names). Free tier: 100,000 a month. */
    MAP_BUDGET_GEOCODE_MONTHLY_CAP("MAP_BUDGET", "GEOCODE_MONTHLY_CAP", 100000, 0, 100000000),
    /** Directions requests. Free tier: 100,000 a month. */
    MAP_BUDGET_DIRECTIONS_MONTHLY_CAP("MAP_BUDGET", "DIRECTIONS_MONTHLY_CAP", 100000, 0, 100000000),
    /** One day may spend at most this share of the month, so one bad day cannot use it all. */
    MAP_BUDGET_DAILY_CAP_PERCENT("MAP_BUDGET", "DAILY_CAP_PERCENT", 10, 1, 100),
    /** Operators are told when a month's count crosses this share of its cap. */
    MAP_BUDGET_WARN_PERCENT("MAP_BUDGET", "WARN_PERCENT", 80, 1, 100);

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
