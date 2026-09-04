package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Optional per-user limits; a null field inherits the matching application config. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserQuotaOverride {
    private UUID userId;
    private Integer freeTripQuota;
    private Integer aiTripFreeQuota;
    private Integer aiTripProQuota;
    private Integer socialLocationDailyLimit;
    private Long dataVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
