package com.ds.goroute.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class UpsertRatePlanRequest {
    @NotBlank @Size(max = 100) private String code;
    @NotBlank @Size(max = 500) private String name;
    @Pattern(regexp = "[A-Z]{3}") private String currency = "VND";
    @NotNull @DecimalMin("0") private BigDecimal basePrice;
    @Pattern(regexp = "ROOM_ONLY|BREAKFAST|HALF_BOARD|FULL_BOARD|ALL_INCLUSIVE")
    private String mealPlan = "ROOM_ONLY";
    private Map<String, Object> cancellationPolicy;
    private Map<String, Object> occupancyPricing;
    @Min(1) private Integer minStay = 1;
    @Min(1) private Integer maxStay;
    @Pattern(regexp = "ENABLED|DISABLED|ARCHIVED") private String status = "ENABLED";
    private Long expectedVersion;
}
