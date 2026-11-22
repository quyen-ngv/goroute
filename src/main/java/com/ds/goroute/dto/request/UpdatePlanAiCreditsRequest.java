package com.ds.goroute.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** A PRO plan's AI credit allowance and how often it refills. */
@Data
public class UpdatePlanAiCreditsRequest {
    @NotNull
    @DecimalMin("0")
    @DecimalMax("10000000")
    private BigDecimal creditsPerRefill;

    /** 1 monthly, 3 quarterly, 12 yearly. */
    @NotNull
    @Min(1)
    @Max(12)
    private Integer refillMonths;
}
