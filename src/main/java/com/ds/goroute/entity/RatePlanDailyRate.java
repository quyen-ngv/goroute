package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RatePlanDailyRate {
    private UUID ratePlanId;
    private LocalDate rateDate;
    private BigDecimal price;
    private Boolean stopSell;
    private Integer minStay;
    private Integer maxStay;
    private Boolean closedToArrival;
    private Boolean closedToDeparture;
    private Integer minAdvanceDays;
    private Integer maxAdvanceDays;
    private Long dataVersion;
    private UUID updatedBy;
    private LocalDateTime updatedAt;
}
