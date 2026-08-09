package com.ds.goroute.type;

import java.util.EnumSet;
import java.util.Set;

public enum MarketplaceBookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CHECKED_IN,
    COMPLETED,
    EXPIRED,
    FAILED,
    CANCELLED_BY_GUEST,
    CANCELLED_BY_HOST,
    CANCELLED_BY_PLATFORM,
    NO_SHOW;

    private static final Set<MarketplaceBookingStatus> INVENTORY_RELEASE_STATUSES = EnumSet.of(
            EXPIRED, FAILED, CANCELLED_BY_GUEST, CANCELLED_BY_HOST, CANCELLED_BY_PLATFORM);

    public boolean canTransitionTo(MarketplaceBookingStatus target) {
        return switch (this) {
            case PENDING_PAYMENT -> EnumSet.of(CONFIRMED, EXPIRED, FAILED, CANCELLED_BY_GUEST,
                    CANCELLED_BY_HOST, CANCELLED_BY_PLATFORM).contains(target);
            case CONFIRMED -> EnumSet.of(CHECKED_IN, NO_SHOW, CANCELLED_BY_GUEST,
                    CANCELLED_BY_HOST, CANCELLED_BY_PLATFORM).contains(target);
            case CHECKED_IN -> target == COMPLETED;
            default -> false;
        };
    }

    public boolean confirmsReservedInventory() {
        return this == CONFIRMED;
    }

    public boolean releasesInventory() {
        return INVENTORY_RELEASE_STATUSES.contains(this);
    }

    public boolean canBeSetByOperator() {
        return this != CANCELLED_BY_GUEST;
    }
}
