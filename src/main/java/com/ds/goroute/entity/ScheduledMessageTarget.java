package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One booking a scheduled message rule is due for, projected in a single query.
 *
 * <p>Carries everything both the placeholder substitution and the send path need, so the job never
 * re-reads the booking aggregate per row.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledMessageTarget {
    /** {@code HOTEL} or {@code ACTIVITY}; exactly one of the two ids below is set. */
    private String bookingType;
    private UUID hotelBookingId;
    private UUID activityOrderId;
    private UUID organizationId;
    private UUID ownerUserId;
    private UUID guestUserId;
    private String bookingCode;
    private String guestName;
    private String propertyName;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private LocalDateTime slotStartsAt;
    private String status;

    public boolean isHotel() { return "HOTEL".equals(bookingType); }
}
