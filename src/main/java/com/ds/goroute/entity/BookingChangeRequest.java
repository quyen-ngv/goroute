package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BookingChangeRequest {
    private UUID id;
    private String bookingType;
    private UUID hotelBookingId;
    private UUID activityOrderId;
    private UUID organizationId;
    private UUID requestedBy;
    private LocalDate newCheckInDate;
    private LocalDate newCheckOutDate;
    private Integer newAdults;
    private Integer newChildren;
    private UUID newSlotId;
    private String message;
    private String status;
    private UUID respondedBy;
    private String responseNote;
    private LocalDateTime respondedAt;
    private BigDecimal priceBefore;
    private BigDecimal priceAfter;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // joined for display
    private String bookingCode;
    private String currency;
    private LocalDateTime newSlotStartsAt;
    private UUID hotelId;
    private UUID activityBookingId;
}
