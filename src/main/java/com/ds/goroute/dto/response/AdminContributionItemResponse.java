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
public class AdminContributionItemResponse {
    private UUID id;
    private UUID userId;
    private String userName;
    private String userAvatarUrl;
    private ContributionStatus status;
    private PendingContributionReviewResponse pendingReview;
    private LocalDateTime createdAt;
}
