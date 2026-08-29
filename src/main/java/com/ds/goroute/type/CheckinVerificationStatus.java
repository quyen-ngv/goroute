package com.ds.goroute.type;

/**
 * Whether the system has evidence that the author was where they say they were.
 *
 * <p>Computed on the server from photo source, GPS accuracy and distance. A client-sent
 * value is ignored: a badge anyone can set is a badge that means nothing, and that would
 * cost the whole trust system its value, not just this feature.
 */
public enum CheckinVerificationStatus {
    VERIFIED,
    UNVERIFIED,
    /** Waiting on something -- moderation, or a name that still has to be resolved. */
    PENDING
}
