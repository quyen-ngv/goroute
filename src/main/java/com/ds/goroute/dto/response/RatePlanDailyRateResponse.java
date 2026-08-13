package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
public class RatePlanDailyRateResponse {
    private UUID ratePlanId;
    private LocalDate rateDate;
    private BigDecimal price;
    private BigDecimal effectivePrice;
    private Boolean stopSell;
    private Integer minStay;
    private Integer maxStay;
    private Boolean closedToArrival;
    private Boolean closedToDeparture;
    private Integer minAdvanceDays;
    private Integer maxAdvanceDays;
    private Long dataVersion;
    private LocalDateTime updatedAt;
}
