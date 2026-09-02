package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Rolling-window quality figures for a partner organization; numeric fields are null until first computed. */
@Data
@Builder
public class PartnerQualityResponse {
    private UUID organizationId;
    private Integer windowDays;
    private LocalDateTime computedAt;
    private Integer bookingsTotal;
    private Integer bookingsConfirmed;
    private BigDecimal hostCancellationRate;
    private BigDecimal noShowRate;
    private BigDecimal expiryRate;
    private Integer avgResponseMinutes;
    private BigDecimal responseWithinSlaRate;
    private Integer reviewCount;
    private BigDecimal reviewAverage;
    private String badge;
}
