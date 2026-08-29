package com.ds.goroute.type;

/**
 * Where a check-in photo came from.
 *
 * <p>Decided by the flow the photo travelled through, never declared by the client: a
 * photo the in-app camera produced is {@code CAMERA}, one that came back from the system
 * picker is {@code GALLERY}. A field the caller can set is a field the caller can lie in,
 * and this value feeds both the verification badge and the reward.
 */
public enum CheckinPhotoSource {
    CAMERA,
    GALLERY,
    /** A check-in whose photos came from both. Treated as the weaker of the two. */
    MIXED;

    public static CheckinPhotoSource combine(CheckinPhotoSource first, CheckinPhotoSource second) {
        if (first == null) {
            return second;
        }
        if (second == null || first == second) {
            return first;
        }
        return MIXED;
    }

    /** Only a photo taken on the spot is evidence of being there. */
    public boolean isLiveCapture() {
        return this == CAMERA;
    }
}
