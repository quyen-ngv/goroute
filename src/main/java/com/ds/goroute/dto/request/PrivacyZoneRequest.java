package com.ds.goroute.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** Create or edit a privacy zone. {@code expectedVersion} is required when editing. */
@Data
public class PrivacyZoneRequest {

    public static final int MIN_RADIUS_M = 200;
    public static final int MAX_RADIUS_M = 1000;

    @NotBlank
    @Size(max = 60)
    private String name;

    @NotNull
    @DecimalMin("-90")
    @DecimalMax("90")
    private BigDecimal latitude;

    @NotNull
    @DecimalMin("-180")
    @DecimalMax("180")
    private BigDecimal longitude;

    @NotNull
    @Min(MIN_RADIUS_M)
    @Max(MAX_RADIUS_M)
    private Integer radiusM;

    @Min(1)
    private Long expectedVersion;
}
