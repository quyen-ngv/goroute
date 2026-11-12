package com.ds.goroute.type;

/** Where a trip story may borrow a photo or video from: the author's own material on that trip. */
public enum PostMediaSource {
    /** A {@code user_checkin_photos} row of one of the author's check-ins. */
    CHECKIN_PHOTO,
    /** A trip memory ({@code media_assets}) the author uploaded to the trip. */
    TRIP_MEMORY
}
