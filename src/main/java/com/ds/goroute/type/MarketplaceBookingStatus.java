package com.ds.goroute.type;

import java.util.EnumSet;
import java.util.Set;

/**
 * Shared lifecycle for hotel bookings and activity orders.
 *
 * <p>Who may set which status is part of the contract, not only which transitions exist:
 * <ul>
 *   <li>Guest: {@link #CANCELLED_BY_GUEST} only, and only while {@link #isGuestCancellable()}.</li>
 *   <li>Partner: {@link #canBeSetByPartner()} — operational outcomes of a stay/visit.</li>
 *   <li>Admin: partner set plus {@link #CANCELLED_BY_PLATFORM}.</li>
 *   <li>System (jobs): {@link #EXPIRED}, {@link #FAILED}. Nobody can set these by hand,
 *       otherwise the reporting meaning of "expired" and "failed" is lost.</li>
 * </ul>
 */
public enum MarketplaceBookingStatus {
    PENDING_PARTNER_CONFIRMATION,
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
    private static final Set<MarketplaceBookingStatus> PARTNER_TARGETS = EnumSet.of(
            CONFIRMED, CHECKED_IN, COMPLETED, NO_SHOW, CANCELLED_BY_HOST);
    private static final Set<MarketplaceBookingStatus> ADMIN_TARGETS = EnumSet.of(
            CONFIRMED, CHECKED_IN, COMPLETED, NO_SHOW, CANCELLED_BY_HOST, CANCELLED_BY_PLATFORM);
    private static final Set<MarketplaceBookingStatus> SYSTEM_ONLY = EnumSet.of(EXPIRED, FAILED);
    private static final Set<MarketplaceBookingStatus> GUEST_CANCELLABLE = EnumSet.of(
            PENDING_PARTNER_CONFIRMATION, CONFIRMED);

    public boolean canTransitionTo(MarketplaceBookingStatus target) {
        return switch (this) {
            case PENDING_PARTNER_CONFIRMATION -> EnumSet.of(CONFIRMED, EXPIRED, FAILED, CANCELLED_BY_GUEST,
                    CANCELLED_BY_HOST, CANCELLED_BY_PLATFORM).contains(target);
            case CONFIRMED -> EnumSet.of(CHECKED_IN, NO_SHOW, CANCELLED_BY_GUEST,
                    CANCELLED_BY_HOST, CANCELLED_BY_PLATFORM).contains(target);
            case CHECKED_IN -> target == COMPLETED;
            default -> false;
        };
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == NO_SHOW || SYSTEM_ONLY.contains(this)
                || this == CANCELLED_BY_GUEST || this == CANCELLED_BY_HOST || this == CANCELLED_BY_PLATFORM;
    }

    public boolean confirmsReservedInventory() {
        return this == CONFIRMED;
    }

    public boolean releasesInventory() {
        return INVENTORY_RELEASE_STATUSES.contains(this);
    }

    /** Statuses a guest may still cancel out of. Time-based cut-offs are enforced by the services. */
    public boolean isGuestCancellable() {
        return GUEST_CANCELLABLE.contains(this);
    }

    public boolean canBeSetByPartner() {
        return PARTNER_TARGETS.contains(this);
    }

    public boolean canBeSetByAdmin() {
        return ADMIN_TARGETS.contains(this);
    }

    public boolean isSystemOnly() {
        return SYSTEM_ONLY.contains(this);
    }
}
