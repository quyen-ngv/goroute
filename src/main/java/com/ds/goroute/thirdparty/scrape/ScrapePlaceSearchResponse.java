package com.ds.goroute.thirdparty.scrape;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScrapePlaceSearchResponse {
    private Boolean success;
    private List<Candidate> candidates = List.of();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Candidate {
        private Integer rank;
        private String title;
        private String googleMapsLink;
        private String resolvedUrl;
        private String placeId;
        private String cid;
        private BigDecimal latitude;
        private BigDecimal longitude;
    }
}
