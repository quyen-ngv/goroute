package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRankProgressResponse {
    private String tierKey;
    private String nextTierKey;
    private int score;
    private int currentTierScore;
    private int nextTierScore;
    private int pointsToNextTier;
    private double progress;
}
