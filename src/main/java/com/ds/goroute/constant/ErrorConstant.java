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
     * The user's subscription has no remaining AI trip generations.
     */
    public static final int AI_TRIP_QUOTA_EXHAUSTED = 4000104;

    /**
     * An AI trip draft can no longer be confirmed.
     */
    public static final int AI_TRIP_DRAFT_INACTIVE = 4000105;

    public static final int REFERRAL_WINDOW_EXPIRED = 4000201;

    public static final int REFERRAL_ALREADY_APPLIED = 4000202;

    public static final int SELF_REFERRAL_NOT_ALLOWED = 4000203;

    public static final int CHECKIN_DISTANCE_LIMIT_EXCEEDED = 4000301;

    public static final int TRIP_BOOK_SLOT_LOCKED = 4000401;

    public static final int TRIP_MEMBER_ALREADY_EXISTS = 4000501;

    public static final int TRIP_INVITATION_NOT_PENDING = 4000502;

    public static final int SYSTEM_CONFIGURATION_NOT_FOUND = 4004208;

    public static final int HTTP_CONNECTION_ERROR  = 4009000;

    public static final int NULL_META_DATA_RESPONSE  = 4009001;

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
