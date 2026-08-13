package com.ds.goroute.type;

import java.util.EnumSet;

/**
 * Payment is intentionally independent from fulfilment, but not every pair is
 * meaningful.  Keep the compatibility rule next to the two state machines so
 * a future payment provider cannot leave an impossible booking/order state.
 */
public enum MarketplacePaymentStatus {
    NOT_COLLECTED,
    UNPAID,
    AUTHORIZED,
    PAID,
    PARTIALLY_REFUNDED,
    REFUNDED,
    FAILED,
    CHARGEBACK;

    public boolean isCompatibleWith(MarketplaceBookingStatus bookingStatus) {
        return switch (bookingStatus) {
            case PENDING_PARTNER_CONFIRMATION -> EnumSet.of(NOT_COLLECTED, UNPAID, AUTHORIZED, PAID, FAILED).contains(this);
            case CONFIRMED, CHECKED_IN -> EnumSet.of(NOT_COLLECTED, UNPAID, AUTHORIZED, PAID, PARTIALLY_REFUNDED).contains(this);
            case COMPLETED, NO_SHOW -> EnumSet.of(NOT_COLLECTED, UNPAID, PAID, PARTIALLY_REFUNDED, REFUNDED, CHARGEBACK).contains(this);
            case EXPIRED, FAILED -> EnumSet.of(NOT_COLLECTED, UNPAID, FAILED, REFUNDED).contains(this);
            case CANCELLED_BY_GUEST, CANCELLED_BY_HOST, CANCELLED_BY_PLATFORM ->
                    EnumSet.of(NOT_COLLECTED, UNPAID, PAID, PARTIALLY_REFUNDED, REFUNDED, CHARGEBACK).contains(this);
        };
    }

    public static void requireCompatible(MarketplaceBookingStatus bookingStatus, String storedPaymentStatus) {
        if (bookingStatus == null) {
            throw new IllegalArgumentException("Booking status is required for payment validation");
        }
        MarketplacePaymentStatus paymentStatus;
        try {
            paymentStatus = valueOf(storedPaymentStatus);
        } catch (NullPointerException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid marketplace payment state: " + storedPaymentStatus, exception);
        }
        if (!paymentStatus.isCompatibleWith(bookingStatus)) {
            throw new IllegalArgumentException("Payment status " + paymentStatus + " is incompatible with " + bookingStatus);
        }
    }
}
