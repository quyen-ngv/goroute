package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class HotelAvailabilityDay {
    private LocalDate inventoryDate;
    private Integer availableUnits;
    private Boolean stopSell;
    private Boolean closedToArrival;
    private Boolean closedToDeparture;
    private Integer minStay;
    private BigDecimal nightlyPrice;
    private String currency;
}
