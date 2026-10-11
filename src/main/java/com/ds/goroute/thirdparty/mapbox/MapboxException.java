package com.ds.goroute.thirdparty.mapbox;

/** Mapbox answered with an error, or could not be reached in time. */
public class MapboxException extends RuntimeException {

    private final int status;

    public MapboxException(String message, int status) {
        super(message);
        this.status = status;
    }

    public MapboxException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
    }

    /** Upstream HTTP status, or 0 when the request never got an answer (timeout, DNS, refused). */
    public int status() {
        return status;
    }
}
