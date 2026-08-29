package com.ds.goroute.type;

/**
 * Where a guide application stands (GUIDE-01).
 *
 * <p>Only {@link #APPROVED} may publish a service. There is no "publish now, verify later"
 * here: this is a service where people meet strangers in unfamiliar places, and checking
 * first is the least the platform can do.
 */
public enum GuideProfileStatus {
    DRAFT,
    PENDING_VERIFICATION,
    APPROVED,
    SUSPENDED,
    REJECTED;

    public boolean canPublishServices() {
        return this == APPROVED;
    }

    public boolean isPubliclyVisible() {
        return this == APPROVED;
    }
}
