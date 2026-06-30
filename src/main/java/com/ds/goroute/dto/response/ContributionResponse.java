package com.ds.goroute.dto.response;

import com.ds.goroute.type.ContributionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContributionResponse {
    private UUID id;
    private UUID groupId;
    private String googleMapsUrl;
    private String placeNameHint;
    private ContributionStatus status;
    private String groupStatus;
    private UUID linkedPlaceId;
    private String linkedPlaceTitle;
    private PendingContributionReviewResponse pendingReview;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
