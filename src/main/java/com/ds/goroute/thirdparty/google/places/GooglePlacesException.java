package com.ds.goroute.thirdparty.google.places;

/** Google Places could not be reached or answered with an error status. */
public class GooglePlacesException extends RuntimeException {

    public GooglePlacesException(String message) {
        super(message);
    }

    public GooglePlacesException(String message, Throwable cause) {
        super(message, cause);
    }
}
