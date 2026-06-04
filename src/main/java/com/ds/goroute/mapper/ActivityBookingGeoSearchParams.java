package com.ds.goroute.mapper;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ActivityBookingGeoSearchParams {
    private final BigDecimal latitude;
    private final BigDecimal longitude;
    private final double radiusKm;
    private final double minLat;
    private final double maxLat;
    private final double minLng;
    private final double maxLng;
    private final BigDecimal minPrice;
    private final BigDecimal maxPrice;
    private final BigDecimal minRating;
    private final int limit;
    private final int offset;
}
