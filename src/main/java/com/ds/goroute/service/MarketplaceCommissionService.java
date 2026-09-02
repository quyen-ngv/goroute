package com.ds.goroute.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Freezes the platform commission onto a booking row at creation time.
 *
 * <p>The commission a partner pays is the one that was in force when the booking was taken.
 * Re-reading {@code host_organizations.commission_percent} at statement time would silently
 * re-price stays that are already in the past whenever an operator adjusts the rate, so the rate
 * and the rule version are copied onto the booking/order row instead and never touched again.
 *
 * <p>Call it from the booking/order create path, right after the row is inserted and inside the
 * same transaction. It is idempotent (the update is guarded by {@code commission_percent IS NULL}),
 * so calling it twice, or letting the statement generator call it again for a row that predates the
 * wiring, is safe.
 */
public interface MarketplaceCommissionService {

    /**
     * Stamps {@code commission_percent}, {@code commission_amount} and {@code commission_rule_version}
     * onto one booking row.
     *
     * @param bookingType {@code "HOTEL"} or {@code "ACTIVITY"} (case-insensitive)
     * @param bookingId   the {@code hotel_bookings.id} / {@code activity_orders.id}
     * @return {@code true} when this call wrote the stamp, {@code false} when the row was already
     *         stamped, missing, or the type was not recognised. Never throws: an accounting stamp
     *         must not be able to fail a guest's booking.
     */
    boolean stampCommission(String bookingType, UUID bookingId);

    /** The rate that would be frozen onto a booking taken for this organization right now. */
    BigDecimal currentCommissionPercent(UUID organizationId);

    /** The commission rule version currently in force ({@code MARKETPLACE_COMMISSION_RULE_VERSION}). */
    String currentRuleVersion();
}
