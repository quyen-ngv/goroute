package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** One bookable date of an activity, across all of its enabled packages. */
@Data @Builder
public class ActivityAvailabilityDayResponse {
    /** Date in the slot's own timezone. */
    private LocalDate date;
    private String currency;
    /** Cheapest adult-facing price of any bookable package that day. */
    private BigDecimal fromPrice;
    private List<PackageDay> packages;

    @Data @Builder
    public static class PackageDay {
        private java.util.UUID packageId;
        private BigDecimal fromPrice;
        private BigDecimal originalPrice;
        private Integer availableQuantity;
        private Integer slotCount;
    }
}
