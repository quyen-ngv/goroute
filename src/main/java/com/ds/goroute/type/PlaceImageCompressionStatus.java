package com.ds.goroute.type;

/** State of a place's image compression (epic 06 §2.13). */
public enum PlaceImageCompressionStatus {
    NOT_COMPRESSED,
    /** A dry run: nothing was written. */
    PREVIEW,
    /** Done and recorded in {@code places.image_compression}; the bulk run skips the place. */
    COMPRESSED,
    /** At least one image FAILED; the others were written but nothing was recorded, so the place is retried. */
    INCOMPLETE
}
