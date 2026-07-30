package com.ds.goroute.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BulkUpdateRoomInventoryRequest {
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;
    @Min(0) private Integer totalUnits;
    @Min(0) private Integer blockedUnits;
    private Boolean stopSell;
    @DecimalMin("0") private BigDecimal priceOverride;
    @Min(1) private Integer minStay;
    private Boolean closedToArrival;
    private Boolean closedToDeparture;
}
