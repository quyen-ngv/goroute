package com.ds.goroute.type;

/**
 * Only {@code ACTIVE} looks, inside their date window, reach the app. {@code ARCHIVED} is the
 * way to retire a look that photos already reference, which is what a delete refuses.
 */
public enum CameraLookStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED
}
