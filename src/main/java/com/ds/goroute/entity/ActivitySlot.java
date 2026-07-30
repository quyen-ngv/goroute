package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ActivitySlot {
    private UUID id; private UUID packageId; private LocalDateTime startsAt; private LocalDateTime endsAt; private String timezone;
    private Integer capacity; private Integer reservedQuantity; private Integer soldQuantity; private Integer blockedQuantity;
    private Integer availableQuantity; private Integer bookingCutoffMinutes; private BigDecimal priceOverride; private String status;
    private Long dataVersion; private UUID updatedBy; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
