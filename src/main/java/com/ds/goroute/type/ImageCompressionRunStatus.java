package com.ds.goroute.type;

/** State of the console's bulk image compression run (epic 00 §9b). */
public enum ImageCompressionRunStatus {
    /** No run since this backend instance started. */
    IDLE,
    RUNNING,
    /** Cancel asked; the image being compressed finishes first. */
    CANCELLING,
    COMPLETED,
    CANCELLED,
    /** Stopped by itself: too many images failed in a row, usually the caesium service is down. */
    STOPPED
}
