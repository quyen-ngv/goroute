package com.ds.goroute.thirdparty.scrape;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapeNationwideJobRequest {
    private String gorouteJobId;
    private String callbackUrl;
    private String importUrl;
    private String callbackToken;
    private Integer maxReviews;
    private Integer selectedReviews;
    private Integer lowStarQuota;
    private Integer minReviewCount;
    private BigDecimal minGoogleRating;
    private BigDecimal minAdjustedRating;
    private Integer searchLimitPerQuery;
    private Integer maxQueriesPerRegion;
    private Boolean headless;
    private List<String> regionCodes;
    private String queryMode;
    private List<String> customQueries;
    private Boolean includeRegionalSpecialties;
    private Boolean includeTouristAreas;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal radiusKm;
    private Integer searchZoom;
    private String duplicateCheckUrl;
}
