package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripDetailResponse {
    private UUID id;
    private String name;
    private String coverImageUrl;
    private String destination;
    private BigDecimal lat;
    private BigDecimal lng;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private BigDecimal budget;
    private String currency;
    private String visibility;
    private String shareCode;
    private String notes;
    private String description;
    
    // Starting point
    private String startingPointName;
    private String startingPointAddress;
    private BigDecimal startingPointLat;
    private BigDecimal startingPointLng;
    private LocalDateTime startingPointTime;
    
    private UserResponse owner;
    private List<TripMemberResponse> members;
    private TripStatsResponse stats;
}
