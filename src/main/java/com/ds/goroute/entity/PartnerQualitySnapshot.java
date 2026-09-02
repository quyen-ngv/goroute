package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerQualitySnapshot {
    private UUID id;
    private UUID organizationId;
    private Integer windowDays;
    private LocalDateTime computedAt;
    private Integer bookingsTotal;
    private Integer bookingsConfirmed;
    private Integer bookingsCancelledByHost;
    private Integer bookingsNoShow;
    private Integer bookingsExpired;
    private Integer avgResponseMinutes;
    private BigDecimal responseWithinSlaRate;
    private BigDecimal hostCancellationRate;
    private BigDecimal noShowRate;
    private BigDecimal expiryRate;
    private Integer reviewCount;
    private BigDecimal reviewAverage;
    private String badge;
}
