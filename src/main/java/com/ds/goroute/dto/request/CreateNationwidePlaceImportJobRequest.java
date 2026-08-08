package com.ds.goroute.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNationwidePlaceImportJobRequest {
    @Builder.Default
    @Min(20) @Max(200)
    private Integer maxReviews = 200;

    @Builder.Default
    @Min(1) @Max(50)
    private Integer selectedReviews = 20;

    @Builder.Default
    @Min(0) @Max(20)
    private Integer lowStarQuota = 4;

    @Builder.Default
    @Min(1)
    private Integer minReviewCount = 101;

    @Builder.Default
    @DecimalMin("0.0") @DecimalMax("5.0")
    private BigDecimal minGoogleRating = BigDecimal.valueOf(4.00);

    @Builder.Default
    @DecimalMin("0.0") @DecimalMax("5.0")
    private BigDecimal minAdjustedRating = BigDecimal.valueOf(3.00);

    @Builder.Default
    @Min(1) @Max(100)
    private Integer searchLimitPerQuery = 40;

    @Builder.Default
    @Min(1) @Max(50)
    private Integer maxQueriesPerRegion = 20;

    @Builder.Default
    private Boolean headless = true;

    private List<String> regionCodes;

    @Builder.Default
    @Pattern(regexp = "APPEND|REPLACE")
    private String queryMode = "APPEND";

    @Size(max = 50)
    private List<@Size(min = 1, max = 200) String> customQueries;

    @Builder.Default
    private Boolean includeRegionalSpecialties = true;

    @Builder.Default
    private Boolean includeTouristAreas = true;

    @DecimalMin("-90.0") @DecimalMax("90.0")
    private BigDecimal latitude;

    @DecimalMin("-180.0") @DecimalMax("180.0")
    private BigDecimal longitude;

    @Builder.Default
    @DecimalMin("0.1") @DecimalMax("100.0")
    private BigDecimal radiusKm = BigDecimal.TEN;

    @Builder.Default
    @Min(3) @Max(21)
    private Integer searchZoom = 14;
}
