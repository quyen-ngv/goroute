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
public class PlaceDetailRefreshCandidateResponse {
    private UUID id;
    private String placeId;
    private String title;
    private String googleMapsLink;
    private String placeGroup;
    private String status;
    private String visibilityStatus;
    private LocalDateTime lastScrapedAt;
}
