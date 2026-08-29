package com.ds.goroute.constant;

public class ErrorConstant {

    public static final int FORBIDDEN = 400003;

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
     * The user has used all free trip-creation slots and needs to unlock one.
     */
    public static final int TRIP_CREATION_QUOTA_EXHAUSTED = 4000101;

    /**
     * The user does not have enough Stars to unlock another trip-creation slot.
     */
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
     * The user's subscription has no remaining AI trip generations.
     */
    public static final int AI_TRIP_QUOTA_EXHAUSTED = 4000104;

    /**
     * An AI trip draft can no longer be confirmed.
     */
    public static final int AI_TRIP_DRAFT_INACTIVE = 4000105;

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

    public static final int SOCIAL_LOCATION_DAILY_LIMIT_REACHED = 4000601;
    public static final int SOCIAL_LOCATION_QUEUE_FULL = 4000602;
    public static final int SOCIAL_LOCATION_TEMPORARILY_BLOCKED = 4000603;
    public static final int SOCIAL_LOCATION_PERMANENTLY_BLOCKED = 4000604;

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

    /**
     * Points wallet (epic 08).
     */
    public static final int INSUFFICIENT_POINTS = 4000901;
    public static final int POINT_TRANSACTION_NOT_REVERSIBLE = 4000902;

    /**
     * Passport (epic 04).
     */
    public static final int PASSPORT_FEATURE_DISABLED = 4001001;
    public static final int REWARD_OUT_OF_STOCK = 4001002;
    public static final int REWARD_NOT_REDEEMABLE = 4001003;

    /**
     * Guide marketplace (epic 07).
     */
    public static final int GUIDE_FEATURE_DISABLED = 4001101;
    public static final int GUIDE_NOT_APPROVED = 4001102;
    public static final int GUIDE_SERVICE_LIMIT_REACHED = 4001103;
    public static final int GUIDE_UNAVAILABLE_ON_DATE = 4001104;
    public static final int GUIDE_CAPACITY_EXCEEDED = 4001105;
    public static final int GUIDE_BOOKING_STATE_INVALID = 4001106;
    public static final int GUIDE_PAYOUT_NOT_RELEASABLE = 4001107;

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
     * Write the error code prefixed with 403 below
     * 403
     */
    public static final int FORBIDDEN_ERROR = 4030001;

    /**
     *  Write the error code prefixed with 404 below
     * 404
     */
    public static final int NOT_FOUND = 4040001;
    public static final int PLACE_NOT_FOUND = 4040002;
    public static final int REVIEW_NOT_FOUND = 4040003;
    public static final int USER_NOT_FOUND = 4040004;
    
    /**
     * Write the error code prefixed with 409 below
     * 409
     */
    public static final int REVIEW_ALREADY_EXISTS = 4090001;
    public static final int ALREADY_PROCESSED = 4090002;
 
    /**
     * Write the error code prefixed with 500 below
     * 500
     */
    public static final int INTERNAL_SERVER_ERROR = 5001001;

}
