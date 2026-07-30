package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data @Builder
public class RatePlanResponse {
    private UUID id;
    private UUID roomTypeId;
    private String code;
    private String name;
    private String currency;
    private BigDecimal basePrice;
    private String mealPlan;
    private Map<String, Object> cancellationPolicy;
    private Map<String, Object> occupancyPricing;
    private Integer minStay;
    private Integer maxStay;
    private String status;
    private Long dataVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
