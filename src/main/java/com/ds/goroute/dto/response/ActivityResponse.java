package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class ActivityResponse {
    private UUID id;
    private UUID tripId;
    private Integer dayNumber;
    private Integer sortOrder;
    private String placeId;
    private UUID customPlaceId;
    private String name;
    private String address;
    private BigDecimal lat;
    private BigDecimal lng;
    
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;
    
    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;
    
    private BigDecimal estimatedCost;
    private String costCurrency;
    private String category;
    private String transportMode;
    private BigDecimal rating;
    private String photoUrl;
    private String notes;
    private String status;
    private Boolean checkedIn;
    private Integer checkedInCount;
    
    // Distance and duration to next activity
    private String distanceToNext;
    private String durationToNext;
    private Integer distanceValueToNext;
    private Integer durationValueToNext;
    
    // Special flags
    private Boolean isAccommodation;
    private Boolean isStartingPoint;
    private LocalDateTime startingPointDate;
    
    // Expense tracking
    private BigDecimal actualSpent;
    private Integer expenseCount;
    private List<ExpenseResponse> expenses;
}
