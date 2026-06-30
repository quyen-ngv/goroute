package com.ds.goroute.entity;

import com.ds.goroute.type.ContributionGroupStatus;
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
public class PlaceContributionGroup {
    private UUID id;
    private String normalizedUrlHash;
    private String googleMapsUrl;
    private String placeNameHint;
    private String resolvedGooglePlaceId;
    private ContributionGroupStatus status;
    private UUID linkedPlaceId;
    private String scrapeJobId;
    private UUID gorouteJobId;
    private String adminNote;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
