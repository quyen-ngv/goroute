package com.ds.goroute.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** An operator's credit correction; positive adds bought credits, negative takes credits away. */
@Data
public class AdminAiCreditAdjustRequest {
    @NotNull
    @DecimalMin("-1000000")
    @DecimalMax("1000000")
    private BigDecimal amount;

    @NotBlank
    @Size(max = 500)
    private String reason;
}
