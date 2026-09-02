package com.ds.goroute.dto.request;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Public hotel search filters; every field optional. Dates + party size make the search availability-aware. */
@Value
@Builder
public class HotelSearchQuery {
    String query;
    String propertyType;
    BigDecimal minPrice;
    BigDecimal maxPrice;
    LocalDate checkIn;
    LocalDate checkOut;
    Integer rooms;
    Integer adults;
    Integer children;

    public boolean hasStay() { return checkIn != null && checkOut != null && checkOut.isAfter(checkIn); }
}
