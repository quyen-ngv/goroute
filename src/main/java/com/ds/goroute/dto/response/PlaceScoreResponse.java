package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceScoreResponse {
    private UUID placeId;
    
    // Scores
    private BigDecimal tripmindScore;
    private BigDecimal googleScore;
    private Integer reviewCount;
    
    // Aspect scores
    private BigDecimal foodScore;
    private BigDecimal priceScore;
    private BigDecimal ambianceScore;
    private BigDecimal serviceScore;
    
    // Nationality breakdown
    private Map<String, BigDecimal> nationalityBreakdown;
    
    // Display logic
    private String displayScore; // "4.3" or "Not enough reviews"
    private String displayLabel; // "TripMind Score" or "Preliminary"
    private Boolean useGoogleScore; // True if < 10 reviews
    
    private LocalDateTime lastCalculatedAt;
}
