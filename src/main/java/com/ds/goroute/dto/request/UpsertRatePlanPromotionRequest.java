package com.ds.goroute.dto.request;

import com.ds.goroute.type.MarketplaceAvailabilityStatus;
import com.ds.goroute.type.PromotionType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class UpsertRatePlanPromotionRequest {
    /** Null applies the promotion to every rate plan of the hotel. */
    private UUID ratePlanId;
    @NotBlank @Size(max = 40) private String code;
    @NotBlank @Size(max = 160) private String name;
    private PromotionType promotionType = PromotionType.BASIC;
    @NotNull @DecimalMin("0.01") @DecimalMax("90.00") private BigDecimal discountPercent;
    @Min(1) @Max(1000) private Integer priority = 100;
    private LocalDate stayStart;
    private LocalDate stayEnd;
    private LocalDate bookStart;
    private LocalDate bookEnd;
    @Min(0) @Max(730) private Integer minAdvanceDays;
    @Min(0) @Max(730) private Integer maxAdvanceDays;
    @Min(1) @Max(90) private Integer minNights;
    private List<DayOfWeek> daysOfWeek;
    private MarketplaceAvailabilityStatus status = MarketplaceAvailabilityStatus.ENABLED;
    private Long expectedVersion;
}
