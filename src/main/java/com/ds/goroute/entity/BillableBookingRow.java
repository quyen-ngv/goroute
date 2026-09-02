package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A booking/order the statement generator has to judge. The query is deliberately wider than the
 * billable set — it returns every finished booking in the period — so the "what is billable" rule
 * lives in one readable Java place instead of being smeared across two SQL statements.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BillableBookingRow {
    private UUID bookingId;
    /** {@code HOTEL} or {@code ACTIVITY}. */
    private String bookingType;
    private String bookingCode;
    private String guestName;
    private LocalDate serviceDate;
    private String currency;
    private BigDecimal totalAmount;
    private String bookingStatus;
    private Boolean guestCharged;
    /** Free text written at cancellation time; carries the penalty when there was one. */
    private String cancellationReason;
    /** Commission frozen on the row at creation time; null until stamped. */
    private BigDecimal commissionPercent;
    private String commissionRuleVersion;
}
