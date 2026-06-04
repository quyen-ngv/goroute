package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTripDraft {
    private UUID id;
    private UUID userId;
    private String tripName;
    private String cityId;
    private String cityName;
    private BigDecimal cityLat;
    private BigDecimal cityLng;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer dayCount;
    private String placeGroups;
    private String pace;
    private String preferenceText;
    private String candidates;
    private String status;
    private String idempotencyKey;
    private UUID createdTripId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
