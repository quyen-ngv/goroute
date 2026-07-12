package com.ds.goroute.dto.response;

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
public class TripDestinationResponse {
    private UUID id;
    private String name;
    private String address;
    private String placeId;
    private BigDecimal lat;
    private BigDecimal lng;
    private Integer orderIndex;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isPrimary;
}
