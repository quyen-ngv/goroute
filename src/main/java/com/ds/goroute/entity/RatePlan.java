package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RatePlan {
    private UUID id;
    private UUID roomTypeId;
    private String code;
    private String name;
    private String currency;
    private BigDecimal basePrice;
    private String mealPlan;
    private String cancellationPolicy;
    private String occupancyPricing;
    private Integer minStay;
    private Integer maxStay;
    private String status;
    private Long dataVersion;
    private UUID createdBy;
    private UUID updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
