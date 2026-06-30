package com.ds.goroute.thirdparty.scrape;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScrapeResolveResponse {
    private String googlePlaceId;
    private String cid;
    private String title;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String normalizedUrl;
}
