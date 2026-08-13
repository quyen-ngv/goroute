package com.ds.goroute.type;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplacePaymentStatusTest {
    @Test
    void supportsPayAtPropertyWithoutPretendingPaymentWasCollected() {
        assertTrue(MarketplacePaymentStatus.NOT_COLLECTED
                .isCompatibleWith(MarketplaceBookingStatus.PENDING_PARTNER_CONFIRMATION));
        assertTrue(MarketplacePaymentStatus.NOT_COLLECTED
                .isCompatibleWith(MarketplaceBookingStatus.COMPLETED));
        assertDoesNotThrow(() -> MarketplacePaymentStatus.requireCompatible(
                MarketplaceBookingStatus.CONFIRMED, MarketplacePaymentStatus.NOT_COLLECTED.name()));
    }

    @Test
    void rejectsAuthorizationAfterARequestHasBeenCancelledOrExpired() {
        assertFalse(MarketplacePaymentStatus.AUTHORIZED
                .isCompatibleWith(MarketplaceBookingStatus.CANCELLED_BY_HOST));
        assertFalse(MarketplacePaymentStatus.AUTHORIZED
                .isCompatibleWith(MarketplaceBookingStatus.EXPIRED));
        assertThrows(IllegalArgumentException.class, () -> MarketplacePaymentStatus.requireCompatible(
                MarketplaceBookingStatus.EXPIRED, MarketplacePaymentStatus.AUTHORIZED.name()));
    }
}
