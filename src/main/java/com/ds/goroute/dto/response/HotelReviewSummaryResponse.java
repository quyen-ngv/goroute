package com.ds.goroute.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * Guest rating shown on a stay, on a 1-5 scale.
 *
 * @param source {@code GOROUTE} when built from reviews written in the app (aspect scores
 *               present), {@code EXTERNAL} when only the imported place rating exists.
 */
@Builder
public record HotelReviewSummaryResponse(
        BigDecimal score,
        Integer reviewCount,
        String source,
        BigDecimal locationScore,
        BigDecimal cleanlinessScore,
        BigDecimal serviceScore,
        BigDecimal facilitiesScore) {
}
