package com.ds.goroute.localevent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Where the event happens. Must resolve to a Vietnamese ward; {@code placeId} is our place UUID. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalEventVenueRequest {

    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String placeId;
}
