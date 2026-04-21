package com.ds.goroute.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTripRequest {
    private String name;
    private String coverImageUrl;
    private LocalDate startDate;
    private LocalDate endDate;
    private String destination;
    private BigDecimal destinationLat;
    private BigDecimal destinationLng;
    private BigDecimal budget;
    private String currency;
    private String status;
    private String visibility;
    private String notes;
    private Boolean shareExpenses;
    private Boolean shareNotes;
    
    // Starting point
    private String startingPointName;
    private String startingPointAddress;
    private BigDecimal startingPointLat;
    private BigDecimal startingPointLng;
    private LocalDateTime startingPointTime;
}
