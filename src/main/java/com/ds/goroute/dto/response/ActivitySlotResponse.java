package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
public class ActivitySlotResponse {
    private UUID id; private UUID packageId; private LocalDateTime startsAt; private LocalDateTime endsAt; private String timezone;
    private Integer capacity; private Integer reservedQuantity; private Integer soldQuantity; private Integer blockedQuantity;
    private Integer availableQuantity; private Integer bookingCutoffMinutes; private BigDecimal priceOverride; private String status;
    private Long dataVersion; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
