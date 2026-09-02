package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data @Builder
public class ActivityOrderResponse {
    private UUID id; private String orderCode; private UUID userId; private UUID organizationId; private UUID activityId;
    private String activityTitle; private UUID slotId; private LocalDateTime slotStartsAt; private LocalDateTime slotEndsAt; private String slotTimezone; private List<Map<String,Object>> participants; private String currency;
    private Map<String,Object> contactInfo; private String specialRequests; private String redemptionStatus; private LocalDateTime redeemedAt; private UUID redeemedBy; private String voucherType; private Boolean guestCharged; private LocalDateTime holdExpiresAt;
    private BigDecimal subtotalAmount; private BigDecimal taxAmount; private BigDecimal feeAmount; private BigDecimal discountAmount;
    private BigDecimal totalAmount; private String orderStatus; private String paymentStatus; private String voucherCode;
    private Map<String,Object> snapshot; private Long dataVersion; private ActivityOrderItemResponse item; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
