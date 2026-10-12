package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** A change Pengoo proposed for a trip, and what became of it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantProposalResponse {
    private UUID id;
    private UUID conversationId;
    private UUID tripId;
    private String action;
    private String status;
    private String summary;
    private Integer dayNumber;
    private Integer toDayNumber;
    private String startTime;
    private String endTime;
    private UUID placeId;
    private String placeName;
    private UUID activityId;
    private String activityName;
    private String category;
    private UUID resolvedByUserId;
    private String resolvedByName;
    private LocalDateTime resolvedAt;
    private UUID resultActivityId;
    private String failureReason;
}
