package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data @Builder
public class HotelBookingResponse {
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
    private Map<String, Object> guestLead;
    private List<Map<String, Object>> guestDetails;
    private String specialRequests;
    private LocalTime estimatedArrivalTime;
    private LocalDateTime holdExpiresAt;
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
    private Map<String, Object> snapshot;
    private Long dataVersion;
    private List<HotelBookingItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
