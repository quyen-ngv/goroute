package com.ds.goroute.thirdparty.scrape;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScrapeJobStatusResponse {
    private String jobId;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private Input input;
    private Result result;
    private ErrorDetail error;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Input {
        private String url;
        private UUID contributionGroupId;
        private UUID gorouteJobId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private String googlePlaceId;
        private String title;
        private Boolean placeAlreadyExists;
        private String importStatus;
        private Integer importHttpStatus;
        private String goroutePlaceId;
        private Integer reviewsPublished;
        private Integer contributorsAdded;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorDetail {
        private String code;
        private String message;
    }
}
