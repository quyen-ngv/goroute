package com.ds.goroute.type;

import lombok.Getter;

@Getter
public enum UserTier {
    NEWCOMER(0.6, 0, 2),
    TRAVELER(0.8, 3, 9),
    EXPLORER(1.0, 10, 29),
    EXPERT(1.4, 30, Integer.MAX_VALUE);
    
    private final double baseWeight;
    private final int minReviews;
    private final int maxReviews;
    
    UserTier(double baseWeight, int minReviews, int maxReviews) {
        this.baseWeight = baseWeight;
        this.minReviews = minReviews;
        this.maxReviews = maxReviews;
    }
    
    public static UserTier fromReviewCount(int reviewCount) {
        for (UserTier tier : values()) {
            if (reviewCount >= tier.minReviews && reviewCount <= tier.maxReviews) {
                return tier;
            }
        }
        return NEWCOMER;
    }
}
