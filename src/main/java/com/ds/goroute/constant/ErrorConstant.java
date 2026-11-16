package com.ds.goroute.constant;

public class ErrorConstant {

    private ErrorConstant() {}

    /**
     * Write the error code prefixed with 200 below
     * 200
     */
    public static final int SUCCESS = 200000;

    /**
     * Write the error code prefixed with 400 below
     * 400
     */

    public static final int BAD_REQUEST = 4000000;

    public static final int INVALID_PARAMETERS = 4000001;

    /**
     * LEGACY-STAR-QUOTA: the balance does not cover AI_CREDIT.TRIP_CREATION_COST. Data:
     * {@code {reason: "CREDIT", feature: "TRIP", balance, required}}. App builds up to 21 only
     * know this code for a refused trip creation; once APP.MIN_BUILD_* passes the first credit
     * build, throw {@link #AI_CREDIT_INSUFFICIENT} with feature TRIP instead and delete this.
     */
    public static final int TRIP_CREATION_QUOTA_EXHAUSTED = 4000101;

    /**
     * LEGACY-STAR-QUOTA: declared for the old Star unlock but never thrown (a short unlock
     * raises {@link #INSUFFICIENT_POINTS}). Delete with the other legacy trip-quota code.
     */
    @Deprecated
    public static final int INSUFFICIENT_STARS_FOR_TRIP_UNLOCK = 4000102;

    /**
     * A free trip has reached its memory-photo limit.
     */
    public static final int FREE_TRIP_MEMORY_LIMIT_REACHED = 4000103;

    /**
     * Only PRO users can upload trip-memory videos.
     */
    public static final int PRO_VIDEO_UPLOAD_REQUIRED = 4000107;

    /**
     * An AI trip draft can no longer be confirmed.
     */

    /**
     * The AI provider did not return a usable response for a required AI-trip decision.
     */
    public static final int AI_TRIP_GENERATION_UNAVAILABLE = 4000106;

    public static final int REFERRAL_WINDOW_EXPIRED = 4000201;

    public static final int REFERRAL_ALREADY_APPLIED = 4000202;

    public static final int SELF_REFERRAL_NOT_ALLOWED = 4000203;

    public static final int CHECKIN_DISTANCE_LIMIT_EXCEEDED = 4000301;

    public static final int TRIP_BOOK_SLOT_LOCKED = 4000401;

    public static final int TRIP_MEMBER_ALREADY_EXISTS = 4000501;

    public static final int TRIP_INVITATION_NOT_PENDING = 4000502;

    /**
     * A guest member cannot be linked to a real account while expenses still name that guest
     * as the payer: linking deletes the guest row, and the expense would be left pointing at a
     * member that no longer exists. The message carries the number of expenses involved, as
     * its first number, so the app can tell the user how much there is to fix.
     */
    public static final int TRIP_GUEST_HAS_EXPENSES = 4000503;

    public static final int SOCIAL_LOCATION_QUEUE_FULL = 4000602;
    public static final int SOCIAL_LOCATION_TEMPORARILY_BLOCKED = 4000603;
    public static final int SOCIAL_LOCATION_PERMANENTLY_BLOCKED = 4000604;

    /** The payer already reminded this person EXPENSE.PAYMENT_REMINDER_DAILY_MAX times today. */
    public static final int EXPENSE_PAYMENT_REMINDER_RATE_LIMITED = 4001101;

    /** Receipt scan: not open to this account (EXPENSE.RECEIPT_SCAN_ENABLED off and not a beta user). */
    public static final int EXPENSE_RECEIPT_SCAN_UNAVAILABLE = 4001102;

    /** Receipt scan: the upload is not a readable image or is over the size limit. */
    public static final int EXPENSE_RECEIPT_SCAN_INVALID_IMAGE = 4001103;

    /**
     * The expense is in another currency than the trip, the app sent no exchange rate and the
     * rate provider had none (epic 05 §2.6). The expense is not saved; it used to be stored 1:1.
     * 4001104 is taken by a retired receipt-scan quota message.
     */
    public static final int EXCHANGE_RATE_UNAVAILABLE = 4001105;

    /**
     * Content moderation (epic 12). The blocked-content code carries the violated policy
     * group in its message so the app can tell the user what to fix instead of showing
     * "invalid content"; the draft is never discarded.
     */
    public static final int CONTENT_BLOCKED_BY_MODERATION = 4000701;
    public static final int IMAGE_REJECTED_BY_MODERATION = 4000702;
    public static final int CONTENT_ALREADY_REPORTED = 4000703;
    public static final int CONTENT_TAKEN_DOWN = 4000704;

    /**
     * Check-in (epic 06).
     */
    public static final int CHECKIN_FEATURE_DISABLED = 4000801;
    public static final int CHECKIN_PHOTO_REQUIRED = 4000802;
    public static final int CHECKIN_GALLERY_NOT_ALLOWED = 4000803;
    public static final int CHECKIN_LOCATION_REQUIRED = 4000804;
    public static final int CHECKIN_PLACE_IMMUTABLE = 4000805;
    public static final int CHECKIN_VIDEO_NOT_ALLOWED = 4000806;
    public static final int CHECKIN_VIDEO_TOO_LONG = 4000807;
    public static final int CHECKIN_MUSIC_NOT_ALLOWED = 4000808;

    /**
     * Points wallet (epic 08).
     */
    public static final int INSUFFICIENT_POINTS = 4000901;
    public static final int POINT_TRANSACTION_NOT_REVERSIBLE = 4000902;

    /** AI_CREDIT.STAR_EXCHANGE_ENABLED is off. */
    public static final int STAR_EXCHANGE_DISABLED = 4000903;

    /**
     * Passport (epic 04).
     */
    public static final int PASSPORT_FEATURE_DISABLED = 4001001;
    public static final int REWARD_OUT_OF_STOCK = 4001002;
    public static final int REWARD_NOT_REDEEMABLE = 4001003;

    /**
     * Partner onboarding (epic 15). The incomplete code carries the step and field that is
     * missing in its message, so the wizard can send the partner back to the right screen
     * instead of showing a generic validation failure on the review page.
     */
    public static final int ONBOARDING_INCOMPLETE = 4001201;
    public static final int ONBOARDING_STEP_UNKNOWN = 4001202;

    /**
     * Marketplace chat (epic 11). Direct conversations can be switched off centrally, and a
     * block is a deliberate refusal rather than a missing conversation.
     */
    public static final int CHAT_DIRECT_DISABLED = 4001301;

    /**
     * Travel tracker (epic 16). An upload whose every point falls outside the trip's days
     * (padded by TRACKER/WINDOW_PAD_DAYS) is refused as a whole rather than stored and hidden.
     */
    public static final int TRACK_OUTSIDE_TRIP_WINDOW = 4001401;

    /** Malformed columnar points: unequal arrays, time not strictly increasing, too many points. */
    public static final int TRACK_CHUNK_INVALID = 4001402;

    /** A user may keep at most five privacy zones. */
    public static final int TRACK_PRIVACY_ZONE_LIMIT = 4001403;

    /** Dismiss / add-to-itinerary only apply to an off-plan stop, not to a planned activity's visit. */
    public static final int TRACK_VISIT_NOT_OFF_PLAN = 4001404;

    /**
     * Meetups (epic 18). The day is outside today..today+MEETUP/MAX_DAYS_AHEAD (Vietnam time),
     * an EXACT time is missing or closer than MEETUP/EXACT_MIN_LEAD_MINUTES, or a FLEXIBLE
     * meetup carries a time.
     */
    public static final int MEETUP_INVALID_TIME = 4001601;

    /** The meetup point is not inside any ward of Vietnam. */
    public static final int MEETUP_OUTSIDE_SERVICE_AREA = 4001602;

    /** The host's own age is outside the age range they chose. */
    public static final int MEETUP_AGE_RANGE_EXCLUDES_HOST = 4001603;

    /** min age not in 18..99, or max age below min age or above 99. */
    public static final int MEETUP_INVALID_AGE_RANGE = 4001604;

    /** An AREA radius other than 300, 1000 or 3000 metres. */
    public static final int MEETUP_INVALID_LOCATION = 4001605;

    /** A rejection with reason OTHER needs a note. */
    public static final int MEETUP_REJECT_NOTE_REQUIRED = 4001606;

    /** A birth year outside 1900..the current year. */
    public static final int MEETUP_INVALID_BIRTH_YEAR = 4001607;

    /**
     * First Apple sign-in without an email claim (email scope not granted). users.email is
     * NOT NULL, so no account can be created; the user must sign in again sharing the email.
     */
    public static final int APPLE_EMAIL_REQUIRED = 4001801;

    /**
     * Local events (epic 17). A console create/update that breaks a field rule; the message
     * names the field and the rule (the same codes the import reports per item).
     */
    public static final int LOCAL_EVENT_INVALID = 4001501;

    /** An import body with no events, more than 100, or not the documented shape. */
    public static final int LOCAL_EVENT_IMPORT_INVALID = 4001502;

    /** An admin API key may only act as a user who holds an admin role. */
    public static final int ADMIN_API_KEY_OWNER_NOT_ADMIN = 4001503;

    /**
     * Posts (epic 20): the body shows an image or video that is not {@code media:<uuid>} of one of
     * this post's own live media (an outside URL, another post's media, a deleted one).
     */
    public static final int POST_INVALID_MEDIA_REF = 4002001;

    /**
     * Place edit suggestions (epic 09): nothing to review - no field change, a description shorter
     * than 10 characters and no photo, or every change equal to what the place already shows.
     */
    public static final int PLACE_EDIT_NO_CHANGES = 4000621;

    public static final int SYSTEM_CONFIGURATION_NOT_FOUND = 4004208;

    public static final int HTTP_CONNECTION_ERROR  = 4009000;

    public static final int NULL_META_DATA_RESPONSE  = 4009001;

    /**
     * File size limit exceeded (413).
     */
    public static final int FILE_TOO_LARGE = 4131001;

    /**
     * Write the error code prefixed with 401 below
     * 401
     */
    public static final int UNAUTHORIZED = 4010001;
    public static final int USERNAME_PASSWORD_WRONG = 4010002;

    /**
     * X-Admin-Api-Key is unknown, revoked, expired, its owner is gone, or the key was sent to
     * a path outside its scope (epic 17). Fails closed: the request never falls through.
     */
    public static final int ADMIN_API_KEY_INVALID = 4011501;

    /**
     * Write the error code prefixed with 403 below
     * 403
     */
    public static final int FORBIDDEN_ERROR = 4030001;

    /** One of the two people has blocked the other, so the message is refused rather than delivered. */
    public static final int CHAT_USER_BLOCKED = 4030003;

    /** The travel tracker is switched off (TRACKER/ENABLED) and the caller is not a beta user. */
    public static final int TRACK_DISABLED = 4031401;

    /** The owner of this track does not share it with the caller. */
    public static final int TRACK_NOT_VISIBLE = 4031402;

    /** Meetups (epic 18) are switched off (MEETUP/ENABLED) and the caller is not a beta user. */
    public static final int MEETUP_DISABLED = 4031601;

    /** The caller's declared age is under 18. */
    public static final int MEETUP_UNDERAGE = 4031602;

    /** The caller's age is outside the meetup's age range. */
    public static final int MEETUP_AGE_NOT_ALLOWED = 4031603;

    /** The host joining their own meetup, a block, or a person the host declined or removed. */
    public static final int MEETUP_JOIN_NOT_ALLOWED = 4031604;

    /** The host may only invite someone they follow or who follows them, with no block between them. */
    public static final int MEETUP_INVITE_NOT_ALLOWED = 4031605;

    /** Local events (epic 17): joining events is switched off (EVENT/ENABLED) and the caller is not a beta user. */
    public static final int LOCAL_EVENT_JOIN_DISABLED = 4031501;

    /** Posts (epic 20): writing in the app is switched off (POST/USER_WRITE_ENABLED) and the caller is not a beta user. */
    public static final int POST_WRITE_NOT_ALLOWED = 4032001;

    /** Place edit suggestions are switched off (PLACE_EDIT/ENABLED) and the caller is not a beta user. */
    public static final int PLACE_EDIT_DISABLED = 4030621;

    /**
     * The place cannot take this suggestion: not ACTIVE, a partner place or one with a live
     * marketplace listing (a closure report is still allowed), or the account is too new.
     */
    public static final int PLACE_EDIT_NOT_ALLOWED = 4030622;

    /** The caller must change a temporary password before using the partner or admin portal. */
    public static final int PASSWORD_CHANGE_REQUIRED = 4031002;

    /**
     *  Write the error code prefixed with 404 below
     * 404
     */
    public static final int NOT_FOUND = 4040001;
    public static final int PLACE_NOT_FOUND = 4040002;
    public static final int REVIEW_NOT_FOUND = 4040003;
    public static final int USER_NOT_FOUND = 4040004;
    /** Also answers a draft that belongs to someone else: a guessed id must not confirm it exists. */
    public static final int ONBOARDING_DRAFT_NOT_FOUND = 4040005;

    /** No such visit in this trip among the caller's own; another person's visit reads the same. */
    public static final int TRACK_VISIT_NOT_FOUND = 4041401;

    /** Meetups (epic 18): no such meetup, or one the caller may not see (same answer on purpose). */
    public static final int MEETUP_NOT_FOUND = 4041601;

    /** The user has no participant row in this meetup. */
    public static final int MEETUP_PARTICIPANT_NOT_FOUND = 4041602;

    /** Asset library (epic 06 §2.5b): no such library asset. */
    public static final int ASSET_LIBRARY_NOT_FOUND = 4040611;

    /** Local events (epic 17): no such event, or (publicly) not published. */
    public static final int LOCAL_EVENT_NOT_FOUND = 4041501;
    public static final int LOCAL_EVENT_IMPORT_BATCH_NOT_FOUND = 4041502;
    public static final int ADMIN_API_KEY_NOT_FOUND = 4041503;

    /** Place edit suggestions: no such suggestion among the caller's own (or, for admins, at all). */
    public static final int PLACE_EDIT_NOT_FOUND = 4040621;
    
    /**
     * Write the error code prefixed with 409 below
     * 409
     */
    public static final int REVIEW_ALREADY_EXISTS = 4090001;
    public static final int ALREADY_PROCESSED = 4090002;

    /** Partner onboarding (epic 15). */
    public static final int ONBOARDING_ALREADY_SUBMITTED = 4090003;
    public static final int ONBOARDING_ORGANIZATION_REQUIRED = 4090004;
    public static final int ONBOARDING_TOO_MANY_DRAFTS = 4090005;

    /** Saved categories (epic 06): renaming onto a name another of the user's categories has. */
    public static final int SAVED_CATEGORY_NAME_TAKEN = 4090601;

    /** Asset library (epic 06 §2.5b): another asset of the collection already has this key. */
    public static final int ASSET_LIBRARY_KEY_TAKEN = 4090611;

    /** Asset library: the asset is still referenced (for example by a food ingredient), so it cannot be deleted. */
    public static final int ASSET_LIBRARY_IN_USE = 4090612;

    /** Place image compression (epic 06 §2.13): the place's images were already compressed (files overwritten). */
    public static final int PLACE_IMAGES_ALREADY_COMPRESSED = 4090613;

    /** Image compression (epic 00 §9b): a bulk run is already going in this backend instance. */
    public static final int IMAGE_COMPRESSION_RUN_ACTIVE = 4090614;

    /** Image compression: the preview key is not an image file of a compression group. */
    public static final int IMAGE_COMPRESSION_KEY_NOT_ALLOWED = 4000614;

    /** Place image compression: the preview URL is not the place's thumbnail or one of its images, or not on our storage. */
    public static final int PLACE_IMAGE_NOT_IN_PLACE = 4000613;

    /** Meetups (epic 18): hosting or joining needs a declared birth year first. */
    public static final int MEETUP_BIRTH_YEAR_REQUIRED = 4091601;

    /** The host already has MEETUP/MAX_ACTIVE_PER_HOST meetups pending or live. */
    public static final int MEETUP_HOST_LIMIT = 4091602;

    /**
     * AI credits (V254): the balance is below what the feature needs to start
     * (AI_CREDIT.MIN_BALANCE_*). Data: {@code {feature, balance, required}}. HTTP 402.
     */
    public static final int AI_CREDIT_INSUFFICIENT = 4020101;

    /**
     * Exchanging these Stars would pass AI_CREDIT.STAR_EXCHANGE_MONTHLY_CAP for the current
     * credit-wallet period. Data: {@code {monthlyCap, usedThisPeriod, remainingThisPeriod,
     * maxStarsThisPeriod}}.
     */
    public static final int STAR_EXCHANGE_CAP_REACHED = 4090901;

    /** The user already has an AI task of this kind running (one at a time per user). */
    public static final int AI_TASK_ALREADY_RUNNING = 4090701;

    /** The user already has MEETUP/MAX_OPEN_REQUESTS_PER_USER join requests waiting. */
    public static final int MEETUP_REQUEST_LIMIT = 4091603;

    /** Every seat (max participants, host excluded) is taken. */
    public static final int MEETUP_FULL = 4091604;

    /** The meetup's window has passed, so it can no longer be reviewed, joined or cancelled. */
    public static final int MEETUP_EXPIRED = 4091605;

    /** The meetup is not in a status the host may edit (approved meetups are never edited). */
    public static final int MEETUP_NOT_EDITABLE = 4091606;

    /** A rejected meetup was already resubmitted MEETUP/MAX_RESUBMITS times. */
    public static final int MEETUP_RESUBMIT_LIMIT = 4091607;

    /** The meetup or the participant row is not in the status this action starts from. */
    public static final int MEETUP_INVALID_STATE = 4091608;

    /** The meetup already has MeetupServiceImpl.MAX_PENDING_INVITES invitations waiting. */
    public static final int MEETUP_INVITE_LIMIT = 4091609;

    /** Place edit suggestions: the suggestion is no longer PENDING (reviewed or cancelled already). */
    public static final int PLACE_EDIT_ALREADY_REVIEWED = 4090621;

    /** Local events (epic 17): the event is cancelled or over, so nobody can join it now. */
    public static final int LOCAL_EVENT_NOT_JOINABLE = 4091501;

    /** Posts (epic 20): the media is still shown by the body ({@code media:<uuid>}), so it cannot be deleted. */
    public static final int POST_MEDIA_IN_USE = 4092001;

    /**
     * Managed accounts (epic 20): the console may only act for an account that is still
     * {@code users.managed_by_admin}; a handed-over (or ordinary) account answers this.
     */
    public static final int ACCOUNT_NOT_MANAGED = 4092002;

    /**
     * Idempotency-Key: a request with this key is still being processed. The client should
     * retry later with the same key and the same request.
     */
    public static final int IDEMPOTENCY_REQUEST_IN_PROGRESS = 4091701;

    /**
     * Write the error code prefixed with 422 below
     * 422
     */
    /**
     * Idempotency-Key: the key was already used by this user for a different request (method,
     * path or body differ). The client must use a new key for a new request.
     */
    public static final int IDEMPOTENCY_KEY_REUSED = 4221701;

    /**
     * Write the error code prefixed with 429 below
     * 429
     */
    /** Identity (epic 01): too many wrong passwords for this account; see LoginLockoutProperties. */
    public static final int LOGIN_TEMPORARILY_LOCKED = 4290101;

    /** Map search (epic 06): too many Goong lookups in the current minute (MAP_SEARCH/*_PER_MINUTE). */
    public static final int MAP_SEARCH_MINUTE_LIMIT_REACHED = 4290601;

    /** Map search (epic 06): the caller used today's Goong lookups (MAP_SEARCH/*_PER_DAY). */
    public static final int MAP_SEARCH_DAILY_LIMIT_REACHED = 4290602;

    /** Meetups (epic 18): more than MEETUP/MAX_SUBMISSIONS_PER_DAY submissions in 24 hours. */
    public static final int MEETUP_RATE_LIMITED = 4291601;

    /** Place edit suggestions: PLACE_EDIT/MAX_SUBMISSIONS_PER_DAY in 24 hours, or MAX_PENDING_PER_USER waiting. */
    public static final int PLACE_EDIT_RATE_LIMITED = 4290621;

    /**
     * Write the error code prefixed with 500 below
     * 500
     */
    public static final int INTERNAL_SERVER_ERROR = 5001001;

    /**
     * Write the error code prefixed with 503 below
     * 503
     */
    /**
     * Media upload: every upload slot is busy (goroute.upload.max-concurrent-uploads) or this
     * user already has goroute.upload.max-concurrent-uploads-per-user running. Sent with
     * Retry-After; the app retries on its own.
     */
    public static final int UPLOAD_BUSY = 5030701;

}
