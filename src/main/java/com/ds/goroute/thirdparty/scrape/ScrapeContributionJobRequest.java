package com.ds.goroute.thirdparty.scrape;

import com.ds.goroute.dto.request.GorouteContributionReviewInput;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapeContributionJobRequest {

    private String url;

    private ImportConfig importConfig;

    private ContributionPayload contribution;

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContributionPayload {
        private UUID gorouteJobId;
        private UUID contributionGroupId;
        private boolean skipPlaceInsertIfExists;
        private List<UUID> contributorUserIds;
        private List<GorouteContributionReviewInput> gorouteReviews;
    }
}
