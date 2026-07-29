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
public class ScrapePlaceDetailRefreshJobRequest {
    private String gorouteJobId;
    private String callbackUrl;
    private String callbackToken;
    private UUID placeId;
    private Integer maxPlaces;
    private Boolean headless;
    private Boolean continueOnError;
}
