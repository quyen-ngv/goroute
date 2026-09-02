package com.ds.goroute.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Changes the organization's commission rate. Existing bookings keep the rate frozen on their own
 * row, so this only affects bookings taken from now on.
 */
@Data
public class UpdatePartnerCommissionRequest {
    @NotNull @DecimalMin("0.00") @DecimalMax("50.00") @Digits(integer = 3, fraction = 2)
    private BigDecimal commissionPercent;
    @NotNull private Long expectedVersion;
}
