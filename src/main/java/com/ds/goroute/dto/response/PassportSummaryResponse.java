package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The passport overview.
 *
 * <p>Three different counts on purpose, because they answer three different questions and
 * conflating them produces numbers users can tell are wrong: visits count occurrences,
 * places count distinct spots, provinces count distinct provinces. Going to Da Lat three
 * times is three visits, one place and one province.
 */
@Data
@Builder
public class PassportSummaryResponse {

    private boolean passportEnabled;

    private long checkinCount;
    private long verifiedCheckinCount;
    private long distinctPlaceCount;
    private int visitedProvinceCount;
    private int totalProvinceCount;

    /**
     * Null until the province data is complete enough to publish. A percentage computed
     * from a half-filled dataset looks like the user's own history is missing.
     */
    private Double completionPercent;

    private int pointsBalance;
    private List<PassportStampResponse> stamps;
    private List<PassportStampProgressResponse> nextStamps;
    /** Place/city proof tags, independently configured from generic achievement stamps. */
    private List<PassportTagResponse> earnedTags;

    private LocalDateTime firstEventAt;
    private LocalDateTime lastEventAt;
}
