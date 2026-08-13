package com.ds.goroute.thirdparty.scrape;

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
    private Integer maxPlaces;
    private String placesUrl;
    private String reviewRefreshUrl;

    @Builder.Default
    private Boolean headless = true;

    @Builder.Default
    private Boolean continueOnError = true;
}
