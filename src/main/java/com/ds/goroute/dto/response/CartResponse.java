package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * The whole cart in one read.
 *
 * <p>{@code estimatedTotal} only adds up the lines that are still available and share
 * {@code currency}; when the cart mixes currencies it is null and the client shows per-line
 * prices instead of inventing a conversion.
 */
@Value
@Builder
public class CartResponse {
    List<CartItemResponse> items;
    int itemCount;
    /** Lines that can no longer be booked as selected. */
    int unavailableCount;
    String currency;
    BigDecimal estimatedTotal;
}
