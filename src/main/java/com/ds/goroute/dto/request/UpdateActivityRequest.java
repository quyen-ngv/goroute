package com.ds.goroute.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateActivityRequest {
    private String placeId;
    private UUID customPlaceId;
    private String name;
    private String address;
    private BigDecimal lat;
    private BigDecimal lng;
    private Integer dayNumber;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal estimatedCost;
    private String costCurrency;
    private String category;
    private String transportMode;
    private String notes;
    private String description;
    private Boolean isAccommodation;
    private Boolean isStartingPoint;
    private LocalDateTime startingPointDate;
    
    // Ignore expenses field if sent from frontend
    private List<Object> expenses;
}
