package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * What a selection costs right now, and whether it can still be booked at all.
 *
 * <p>This is a read: it takes no inventory. A quote that says {@code available} is a statement
 * about this instant only — createBooking / createOrder re-run the same rules under a lock and
 * remain the single source of truth for the price a guest is charged.
 */
@Value
@Builder
public class MarketplaceQuoteResponse {
    boolean available;
    /** Why it cannot be booked (null when available), in the wording the pricing rules produced. */
    String unavailableReason;
    String currency;
    /** Total for the whole selection (all rooms / all tickets). Null when not available. */
    BigDecimal totalAmount;
}
