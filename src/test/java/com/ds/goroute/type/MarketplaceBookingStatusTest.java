package com.ds.goroute.type;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceBookingStatusTest {
    @Test
    void sharedTransitionRulesRejectSkippingCheckIn() {
        assertTrue(MarketplaceBookingStatus.PENDING_PARTNER_CONFIRMATION
                .canTransitionTo(MarketplaceBookingStatus.CONFIRMED));
        assertTrue(MarketplaceBookingStatus.CONFIRMED
                .canTransitionTo(MarketplaceBookingStatus.CHECKED_IN));
        assertFalse(MarketplaceBookingStatus.CONFIRMED
                .canTransitionTo(MarketplaceBookingStatus.COMPLETED));
        assertTrue(MarketplaceBookingStatus.CHECKED_IN
                .canTransitionTo(MarketplaceBookingStatus.COMPLETED));
    }

    @Test
    void inventoryFlagsMatchTheBookingLifecycle() {
        assertTrue(MarketplaceBookingStatus.CONFIRMED.confirmsReservedInventory());
        assertTrue(MarketplaceBookingStatus.CANCELLED_BY_HOST.releasesInventory());
        assertTrue(MarketplaceBookingStatus.EXPIRED.releasesInventory());
        assertFalse(MarketplaceBookingStatus.NO_SHOW.releasesInventory());
    }

    @Test
    void actorsMayOnlySetTheStatusesThatBelongToThem() {
        // Guest-only outcome
        assertFalse(MarketplaceBookingStatus.CANCELLED_BY_GUEST.canBeSetByPartner());
        assertFalse(MarketplaceBookingStatus.CANCELLED_BY_GUEST.canBeSetByAdmin());
        // System-only outcomes: a partner or admin typing EXPIRED would corrupt reporting
        assertTrue(MarketplaceBookingStatus.EXPIRED.isSystemOnly());
        assertFalse(MarketplaceBookingStatus.EXPIRED.canBeSetByPartner());
        assertFalse(MarketplaceBookingStatus.EXPIRED.canBeSetByAdmin());
        assertFalse(MarketplaceBookingStatus.FAILED.canBeSetByAdmin());
        // Platform cancel is admin-only
        assertFalse(MarketplaceBookingStatus.CANCELLED_BY_PLATFORM.canBeSetByPartner());
        assertTrue(MarketplaceBookingStatus.CANCELLED_BY_PLATFORM.canBeSetByAdmin());
        // Operational outcomes belong to the partner
        assertTrue(MarketplaceBookingStatus.CANCELLED_BY_HOST.canBeSetByPartner());
        assertTrue(MarketplaceBookingStatus.NO_SHOW.canBeSetByPartner());
        assertTrue(MarketplaceBookingStatus.COMPLETED.canBeSetByPartner());
    }

    @Test
    void guestCanOnlyCancelBeforeTheStayStarts() {
        assertTrue(MarketplaceBookingStatus.PENDING_PARTNER_CONFIRMATION.isGuestCancellable());
        assertTrue(MarketplaceBookingStatus.CONFIRMED.isGuestCancellable());
        assertFalse(MarketplaceBookingStatus.CHECKED_IN.isGuestCancellable());
        assertFalse(MarketplaceBookingStatus.COMPLETED.isGuestCancellable());
        assertFalse(MarketplaceBookingStatus.EXPIRED.isGuestCancellable());
    }
}
