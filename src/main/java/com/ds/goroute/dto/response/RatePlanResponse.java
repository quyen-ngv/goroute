package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class RatePlanResponse {
    private UUID id;
    private UUID roomTypeId;
    private String code;
    private String name;
    private String description;
    private String currency;
    private BigDecimal basePrice;
    private String pricingModel;
    private Integer baseOccupancy;
    private BigDecimal extraAdultFee;
    private BigDecimal extraChildFee;
    private String mealPlan;
    private List<String> includedBenefits;
    private Map<String, Object> cancellationPolicy;
    private Map<String, Object> prepaymentPolicy;
    private Map<String, Object> noShowPolicy;
    private Map<String, Object> occupancyPricing;
    private Integer minStay;
    private Integer maxStay;
    private Integer minAdvanceDays;
    private Integer maxAdvanceDays;
    private Boolean refundable;
    private String status;
    private Long dataVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
