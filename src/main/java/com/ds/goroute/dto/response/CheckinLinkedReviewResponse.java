package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** The review created by a rated check-in, carried with that post instead of rendered separately. */
@Data
@Builder
public class CheckinLinkedReviewResponse {
    private UUID id;
    private Integer overallRating;
    private Integer foodRating;
    private Integer priceRating;
    private Integer ambianceRating;
    private Integer serviceRating;
    private String text;
    private List<String> photos;
    private Integer helpfulVotes;
    private Integer unhelpfulVotes;
    private LocalDateTime updatedAt;
}
