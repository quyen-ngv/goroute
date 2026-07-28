package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class NationwidePlaceImportResponse {
    private boolean imported;
    private String outcome;
    private UUID placeId;
    private String googlePlaceId;
    private BigDecimal avgAuthenticityScore;
    private BigDecimal placeOverallScore;
    private BigDecimal adjustedRating;
    private Integer scoredReviewCount;
    private Integer selectedReviewCount;
    private Integer selectedLowStarCount;
}
