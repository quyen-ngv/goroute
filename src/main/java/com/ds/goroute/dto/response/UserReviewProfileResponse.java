package com.ds.goroute.dto.response;

import com.ds.goroute.type.UserTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReviewProfileResponse {
    private UUID userId;
    private UserTier tier;
    private BigDecimal trustScore;
    
    // Stats
    private Integer reviewCount;
    private Integer avgReviewLength;
    private Integer helpfulVotesReceived;
    private Integer verifiedTripsCount;
    
    // Display
    private String tierDisplay; // "Explorer 🧭"
    private String tierDescription; // "10-29 reviews"
}
