package com.ds.goroute.service.placelookup;

/** A provider could not answer: no credentials, an error status, or a body it could not read. */
public class PlaceLookupException extends RuntimeException {

    public PlaceLookupException(String message) {
        super(message);
    }

    public PlaceLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
