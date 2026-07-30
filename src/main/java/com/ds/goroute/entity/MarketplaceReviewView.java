package com.ds.goroute.entity;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MarketplaceReviewView {
    private UUID reviewId; private UUID reviewerUserId; private String reviewerName;
    private UUID placeId; private UUID hotelId; private UUID activityBookingId; private String subjectName;
    private Integer overallRating; private String reviewText; private String photos; private LocalDateTime reviewCreatedAt;
    private UUID responseId; private UUID organizationId; private UUID responderUserId; private String responderName;
    private String responseText; private String responseStatus; private Long responseVersion;
    private LocalDateTime responseCreatedAt; private LocalDateTime responseUpdatedAt;
}
