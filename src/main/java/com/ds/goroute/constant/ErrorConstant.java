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