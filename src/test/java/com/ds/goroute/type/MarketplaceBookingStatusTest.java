package com.ds.goroute.type;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceBookingStatusTest {
    @Test
    void sharedTransitionRulesRejectSkippingCheckIn() {
        assertTrue(MarketplaceBookingStatus.PENDING_PAYMENT
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
        assertFalse(MarketplaceBookingStatus.CANCELLED_BY_GUEST.canBeSetByOperator());
    }
}
