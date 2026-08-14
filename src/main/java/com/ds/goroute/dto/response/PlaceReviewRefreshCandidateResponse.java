package com.ds.goroute.dto.response;

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
public class PlaceReviewRefreshCandidateResponse {
    private UUID id;
    private String placeId;
    private String title;
    private String googleMapsLink;
    private String visibilityStatus;
    private Integer scoreSampleCount;
    private LocalDateTime scoreCalculatedAt;
    private LocalDateTime lastScrapedAt;
    private Integer storedReviewCount;
    private Integer imageReviewCount;
    private Integer managedImageReviewCount;
    private Integer externalImageReviewCount;
    private Integer googleImageReviewCount;
    private Integer recalculationNeededCount;
}
