package com.ds.goroute.type;

/**
 * What one media item of a check-in is. Stored by name in {@code media_assets.media_type},
 * whose CHECK constraint (V116) allows exactly these names.
 *
 * <p>A check-in is either photos or a single video, never both.
 */
public enum CheckinMediaType {
    IMAGE,
    VIDEO
}
