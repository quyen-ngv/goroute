package com.ds.goroute.type;

/** What place image compression did with one image (epic 06 §2.13). */
public enum PlaceImageCompressionOutcome {
    /** The WebP overwrote the file under its own key. */
    COMPRESSED,
    /** Dry run: would have overwritten the file. */
    WOULD_COMPRESS,
    /** The WebP did not save the minimum share of bytes; the file was left alone. */
    NO_SAVINGS,
    /** Already compressed by any path (bulk run, upload hook, an earlier place); not read again. */
    ALREADY_COMPRESSED,
    /** Not a still JPEG, PNG or WebP (for example an animated WebP); left alone. */
    NOT_IMAGE,
    /** Not on our storage (for example a Google URL); never touched. */
    EXTERNAL,
    /** The URL points into our storage but the object is not there. */
    MISSING,
    /** Reading, compressing or writing failed; the file was left alone. */
    FAILED
}
