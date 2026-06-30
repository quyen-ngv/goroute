package com.ds.goroute.entity;

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
public class PlaceContributionImportLog {
    private UUID id;
    private UUID contributionGroupId;
    private UUID gorouteJobId;
    private String scrapeJobId;
    private UUID goroutePlaceId;
    private Boolean placeAlreadyExists;
    private Integer reviewsPublished;
    private Integer contributorsAdded;
    private LocalDateTime processedAt;
}
