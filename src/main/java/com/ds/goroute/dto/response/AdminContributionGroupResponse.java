package com.ds.goroute.dto.response;

import com.ds.goroute.type.ContributionGroupStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminContributionGroupResponse {
    private UUID id;
    private String googleMapsUrl;
    private String placeNameHint;
    private String resolvedGooglePlaceId;
    private ContributionGroupStatus status;
    private UUID linkedPlaceId;
    private String linkedPlaceTitle;
    private String scrapeJobId;
    private UUID gorouteJobId;
    private String adminNote;
    private String rejectionReason;
    private int contributorCount;
    private List<AdminContributionItemResponse> contributions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
