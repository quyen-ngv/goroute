package com.ds.goroute.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What cancelling would cost for a stay that is not booked yet, in the property's local time.
 *
 * @param freeUntil     last moment a cancellation is free; null for non-refundable rates or when that moment has passed
 * @param penaltyAmount charged when cancelling after {@code freeUntil}, in the offer currency
 */
@Builder
public record HotelCancellationTermsResponse(
        String policyType,
        boolean refundable,
        LocalDateTime freeUntil,
        BigDecimal penaltyAmount,
        String penaltyRule) {
}
