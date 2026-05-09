package com.ds.goroute.entity;

import com.ds.goroute.type.UserTier;
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
public class UserReviewProfile {
    private UUID userId;
    private UserTier tier;
    private BigDecimal trustScore;
    
    // Stats
    private Integer reviewCount;
    private Integer avgReviewLength;
    private Integer helpfulVotesReceived;
    private Integer verifiedTripsCount;
    
    private LocalDateTime updatedAt;
}
