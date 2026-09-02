package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One aggregate row over a booking table (hotel bookings or activity orders) for a quality window.
 * Counts are whole numbers; the response figures are already in minutes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerBookingAggregate {
    private Integer total;
    private Integer confirmed;
    private Integer cancelledByHost;
    private Integer noShow;
    private Integer expired;
    /** Number of bookings that have a measurable partner response. */
    private Integer responded;
    /** Number of responded bookings answered within the SLA. */
    private Integer respondedWithinSla;
    /** Sum of response minutes over responded bookings. */
    private BigDecimal responseMinutesSum;
}
