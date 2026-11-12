package com.ds.goroute.type;

/**
 * Whether a post's media is a photo or a video (media_assets.media_type). The markdown does not
 * say: {@code ![](media:id)} draws whichever kind the row is.
 */
public enum PostMediaType {
    IMAGE,
    /** url is the video file; thumbnail_url is the poster frame, which a video must have. */
    VIDEO
}
