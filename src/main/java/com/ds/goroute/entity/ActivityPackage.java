package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ActivityPackage {
    private UUID id; private UUID activityBookingId; private String code; private String name; private String description;
    private String currency; private BigDecimal basePrice; private Integer minQuantity; private Integer maxQuantity;
    private String attributes; private String cancellationPolicy; private String status; private Long dataVersion;
    private UUID createdBy; private UUID updatedBy; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
