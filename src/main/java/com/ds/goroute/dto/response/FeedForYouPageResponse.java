package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One page of the ranked "For you" feed (GET /v1/api/feed/for-you). When {@link #enabled} is
 * false the viewer is not on the ranked feed yet and the app keeps its three-source feed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeedForYouPageResponse {

    private boolean enabled;
    /** "ranked" or "legacy": which feed the viewer is on, for the app's analytics. */
    private String variant;
    private String sessionId;
    /** Send back as {@code cursor} for the next page; null at the end. */
    private String nextCursor;
    private boolean hasMore;
    /** How far the ranking had to widen (L1..L5); L5 means the viewer has seen it all. */
    private String fillLevel;
    /** The previous cursor's ranking had expired and this page starts a new one. */
    private boolean restarted;
    /** The trip the ranking is leaning towards, for the app's context line; null when none. */
    private Context context;
    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        /** CHECKIN, REVIEW or TRIP; exactly the matching payload is set. */
        private String type;
        private UserCheckinResponse checkin;
        private UserReviewResponse review;
        private PublicTripResponse trip;
        /** Only for USER.BETA_USER asking with debug=true. */
        private Rank rank;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Context {
        private UUID tripId;
        private String tripName;
        private String provinceName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Rank {
        private double score;
        private List<String> sources;
        private Map<String, Double> factors;
        private String context;
    }
}
