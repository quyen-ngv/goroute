package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class MarketplaceReviewViewResponse {
    private UUID reviewId;
    private UUID reviewerUserId;
    private String reviewerName;
    private UUID placeId;
    private UUID hotelId;
    private UUID activityId;
    private String subjectName;
    private Integer overallRating;
    private String reviewText;
    private List<String> photos;
    private LocalDateTime reviewCreatedAt;
    private UUID responseId;
    private UUID organizationId;
    private UUID responderUserId;
    private String responderName;
    private String responseText;
    private String responseStatus;
    private Long responseVersion;
    private LocalDateTime responseCreatedAt;
    private LocalDateTime responseUpdatedAt;
}
