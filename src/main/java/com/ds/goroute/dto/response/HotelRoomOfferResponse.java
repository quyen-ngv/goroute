package com.ds.goroute.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * One room type with every enabled rate priced for the requested stay.
 *
 * @param fromNightlyPrice cheapest average nightly price among available rates; the cheapest base price
 *                         when none is available, so a sold-out room still shows a reference price
 */
@Builder
public record HotelRoomOfferResponse(
        RoomTypeResponse room,
        BigDecimal fromNightlyPrice,
        String currency,
        boolean available,
        List<HotelRateOfferResponse> rates) {
}
