package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** Null quota fields inherit the application-wide value. */
@Data
public class UpdateUserQuotaOverridesRequest {
    @Min(0)
    @Max(1000)
    private Integer freeTripQuota;

    @Min(0)
    @Max(1000)
    private Integer aiTripFreeQuota;

    @Min(0)
    @Max(1000)
    private Integer aiTripProQuota;

    @Min(0)
    @Max(1000)
    private Integer socialLocationDailyLimit;

    @Min(0)
    private Long expectedVersion;
}
