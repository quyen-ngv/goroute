package com.ds.goroute.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.Map;
import com.ds.goroute.type.RatePlanCalendarClearField;

@Data
public class BulkUpdateRatePlanCalendarRequest {
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;
    private Set<DayOfWeek> daysOfWeek;
    @NotNull private Map<LocalDate, @Min(0) Long> expectedVersions;
    @DecimalMin("0") private BigDecimal price;
    private Boolean stopSell;
    @Min(1) private Integer minStay;
    @Min(1) private Integer maxStay;
    private Boolean closedToArrival;
    private Boolean closedToDeparture;
    @Min(0) private Integer minAdvanceDays;
    @Min(0) private Integer maxAdvanceDays;
    /** Null keeps a daily override unchanged; listed fields reset to the rate-plan default. */
    private Set<RatePlanCalendarClearField> clearFields;
}
