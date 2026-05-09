package com.ds.goroute.entity;

import com.ds.goroute.type.ActivityStatus;
import com.ds.goroute.type.TransportMode;
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
public class Activity {
    private UUID id;
    private UUID tripId;
    private Integer dayNumber;
    private String placeId;
    private UUID customPlaceId;  // Custom place reference
    private String name;
    private String address;
    private BigDecimal lat;
    private BigDecimal lng;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal estimatedCost;
    private String costCurrency;
    private String category;
    private TransportMode transportMode;
    private BigDecimal rating;
    private String photoUrl;
    private String notes;
    private String description;
    private ActivityStatus status;
    private UUID addedBy;
    
    // Special flags
    private Boolean isAccommodation;
    private Boolean isStartingPoint;
    private LocalDateTime startingPointDate;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
