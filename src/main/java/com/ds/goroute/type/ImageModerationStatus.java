package com.ds.goroute.type;

/** Where one stored image is in the background moderation check. */
public enum ImageModerationStatus {
    /** Stored and visible; the check has not finished yet. */
    PENDING,
    /** The check finished (or was recorded as failed); the decision column holds the result. */
    DONE
}
