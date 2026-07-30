package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class HotelBookingItem {
    private UUID id;
    private UUID bookingId;
    private UUID roomTypeId;
    private UUID ratePlanId;
    private String roomTypeName;
    private String ratePlanName;
    private Integer quantity;
    private Integer adults;
    private Integer children;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private String snapshot;
    private LocalDateTime createdAt;
}
