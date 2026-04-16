package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateActivityRequest {
    private String placeId;
    private UUID customPlaceId;

    @NotBlank(message = "Activity name is required")
    private String name;

    private String address;
    private BigDecimal lat;
    private BigDecimal lng;

    @NotNull(message = "Day number is required")
    private Integer dayNumber;

    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal estimatedCost;
    private String costCurrency;
    private String category;
    private String transportMode;
    private String notes;
    
    // Special flags
    private Boolean isAccommodation;
    private Boolean isStartingPoint;
    private LocalDateTime startingPointDate;
}
