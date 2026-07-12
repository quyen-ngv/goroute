package com.ds.goroute.thirdparty.scrape;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapePlaceImportJobRequest {
    private String url;

    @JsonProperty("max_reviews")
    private Integer maxReviews;

    @JsonProperty("max_scrolls")
    private Integer maxScrolls;

    private Boolean headless;
    private String visibilityStatus;
    private ImportConfig importConfig;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportConfig {
        private boolean enabled;
        private String url;
        private String method;
        private Map<String, String> headers;
    }
}
