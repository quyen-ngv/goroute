package com.ds.goroute.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTripDestinationSnapshot {
    private UUID locationImageId;
    private String citySlug;
    private String name;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer orderIndex;
}
