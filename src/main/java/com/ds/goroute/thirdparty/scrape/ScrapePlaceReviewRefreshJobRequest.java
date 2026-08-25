package com.ds.goroute.thirdparty.scrape;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapePlaceReviewRefreshJobRequest {
    private UUID placeId;

    @JsonProperty("max_reviews")
    private Integer maxReviews;

    private Integer maxPlaces;
    private String placesUrl;
    private String reviewRefreshUrl;

    @Builder.Default
    private Boolean headless = true;

    @Builder.Default
    private Boolean continueOnError = true;
}
