package com.ds.goroute.dto.request;

/**
 * The camera look key format, shared by the request validators and mirrored by the
 * {@code camera_looks} CHECK. The app's bundled looks use the same format, so a check-in photo's
 * {@code filterKey} / {@code frameKey} is checked against it without looking the key up.
 */
public final class CameraLookKeys {

    public static final String PATTERN = "^[a-z0-9]+(_[a-z0-9]+)*$";

    public static final int MAX_LENGTH = 80;

    private CameraLookKeys() {
    }
}
