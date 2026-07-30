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
public class HotelBooking {
    private UUID id;
    private String bookingCode;
    private UUID userId;
    private UUID organizationId;
    private UUID hotelId;
    private String hotelName;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer adults;
    private Integer children;
    private String guestLead;
    private String currency;
    private BigDecimal subtotalAmount;
    private BigDecimal taxAmount;
    private BigDecimal feeAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String bookingStatus;
    private String paymentStatus;
    private String source;
    private String cancellationReason;
    private LocalDateTime cancelledAt;
    private String snapshot;
    private Long dataVersion;
    private UUID createdBy;
    private UUID updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
