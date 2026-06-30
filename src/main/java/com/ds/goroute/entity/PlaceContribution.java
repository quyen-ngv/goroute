package com.ds.goroute.entity;

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
public class PlaceContribution {
    private UUID id;
    private UUID groupId;
    private UUID userId;
    private String googleMapsUrl;
    private String placeNameHint;
    private ContributionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
