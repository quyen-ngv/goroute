package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One confirmed/checked-in stay of a room type, as exported to the iCal feed. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MarketplaceIcalBooking {
    private UUID bookingId;
    private String bookingCode;
    private String bookingStatus;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer quantity;
    private LocalDateTime updatedAt;
}
