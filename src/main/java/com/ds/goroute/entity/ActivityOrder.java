package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ActivityOrder {
    private UUID id; private String orderCode; private UUID userId; private UUID organizationId; private UUID activityBookingId;
    private String activityTitle; private UUID slotId; private LocalDateTime slotStartsAt; private LocalDateTime slotEndsAt; private String slotTimezone; private String participantInfo; private String currency;
    private String contactInfo; private String specialRequests; private String redemptionStatus; private LocalDateTime redeemedAt; private UUID redeemedBy; private Boolean guestCharged; private String idempotencyKey; private LocalDateTime holdExpiresAt;
    private BigDecimal subtotalAmount; private BigDecimal taxAmount; private BigDecimal feeAmount; private BigDecimal discountAmount;
    private BigDecimal totalAmount; private String orderStatus; private String paymentStatus; private String voucherCode;
    private String snapshot; private Long dataVersion; private UUID createdBy; private UUID updatedBy;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
