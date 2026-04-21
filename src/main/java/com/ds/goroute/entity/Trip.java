package com.ds.goroute.entity;

import com.ds.goroute.type.TripStatus;
import com.ds.goroute.type.TripVisibility;
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
public class Trip {
    private UUID id;
    private String name;
    private String coverImageUrl;
    private String destination;
    private String destinationPlaceId;
    private BigDecimal destinationLat;
    private BigDecimal destinationLng;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal budget;
    private String currency;
    private TripStatus status;
    private TripVisibility visibility;
    private String shareCode;
    private String timezone;
    private UUID ownerId;
    
    // Starting point fields
    private String startingPointName;
    private String startingPointAddress;
    private BigDecimal startingPointLat;
    private BigDecimal startingPointLng;
    private LocalDateTime startingPointTime;
    
    private Boolean shareExpenses;
    private Boolean shareNotes;
    
    private Boolean isDeleted;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
