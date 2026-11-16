package com.ds.goroute.type;

/** What compressing one stored image did. The first three are recorded so a later run skips the key. */
public enum ImageCompressionOutcome {
    /** The WebP overwrote the file under its own key. */
    COMPRESSED,
    /** The WebP did not save the minimum share of bytes; the file was left alone. */
    NO_SAVINGS,
    /** The bytes are not a still JPEG, PNG or WebP (for example an animated WebP); left alone. */
    NOT_IMAGE,
    /** Dry run: would have overwritten the file. Never recorded. */
    WOULD_COMPRESS,
    /** Already in {@code image_compressions}; not read again. Never recorded. */
    ALREADY_DONE,
    /** No object under the key. Never recorded. */
    MISSING,
    /** Reading, compressing or writing failed; the file was left alone. Never recorded, so retried. */
    FAILED;

    public boolean isRecorded() {
        return this == COMPRESSED || this == NO_SAVINGS || this == NOT_IMAGE;
    }
}
