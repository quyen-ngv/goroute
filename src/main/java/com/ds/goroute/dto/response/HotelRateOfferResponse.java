package com.ds.goroute.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * A rate plan priced for one stay. Amounts cover the whole party and all nights, in the guest's
 * display currency, and already include taxes and fees.
 *
 * @param unavailableReason a {@link com.ds.goroute.type.HotelOfferUnavailableReason} name, null when available
 * @param availableUnits    fewest rooms left on any night; null when the rate is not open every night
 * @param originalTotalPrice total before promotions; null when no night is discounted
 * @param taxesAndFeesAmount the VAT and service-charge share included in {@code totalPrice}
 */
@Builder
public record HotelRateOfferResponse(
        RatePlanResponse ratePlan,
        boolean available,
        String unavailableReason,
        Integer availableUnits,
        Integer nights,
        BigDecimal totalPrice,
        BigDecimal originalTotalPrice,
        BigDecimal nightlyAveragePrice,
        String promotionCode,
        BigDecimal promotionPercent,
        BigDecimal taxesAndFeesAmount,
        String currency,
        HotelCancellationTermsResponse cancellation) {
}
