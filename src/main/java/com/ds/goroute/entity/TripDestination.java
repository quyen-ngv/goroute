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
public class TripDestination {
    private UUID id;
    private UUID tripId;
    private String name;
    private String address;
    private String placeId;
    private BigDecimal lat;
    private BigDecimal lng;
    private Integer orderIndex;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isPrimary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
