package com.ds.goroute.dto.request;

import com.ds.goroute.type.MarketplaceAvailabilityStatus;
import com.ds.goroute.type.MealPlan;

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
    private MealPlan mealPlan = MealPlan.ROOM_ONLY;
    private Map<String, Object> cancellationPolicy;
    private Map<String, Object> occupancyPricing;
    @NotNull @Min(1) private Integer minStay = 1;
    @Min(1) private Integer maxStay;
    private MarketplaceAvailabilityStatus status = MarketplaceAvailabilityStatus.ENABLED;
    private Long expectedVersion;
}
