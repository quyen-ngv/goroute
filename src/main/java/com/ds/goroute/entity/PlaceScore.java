package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceScore {
    private UUID placeId;
    
    // Scores
    private BigDecimal tripmindScore;
    private BigDecimal googleScore;
    
    // Review count
    private Integer reviewCount;
    
    // Aspect scores
    private BigDecimal foodScore;
    private BigDecimal priceScore;
    private BigDecimal ambianceScore;
    private BigDecimal serviceScore;
    
    // Nationality breakdown (JSON)
    private String nationalityBreakdown;
    
    private LocalDateTime lastCalculatedAt;
}
