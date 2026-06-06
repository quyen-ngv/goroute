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
public class UserReview {
    private UUID id;
    private UUID userId;
    private UUID placeId;
    private UUID activityBookingId;
    private UUID tripId;
    
    // Ratings
    private Integer overallRating;
    private Integer foodRating;
    private Integer priceRating;
    private Integer ambianceRating;
    private Integer serviceRating;
    
    // Content
    private String text;
    private String photos; // JSON array
    
    // Scoring
    private BigDecimal weight;
    private Integer helpfulVotes;
    private Integer unhelpfulVotes;
    
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
