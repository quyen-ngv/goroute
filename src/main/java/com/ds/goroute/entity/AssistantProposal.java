package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** A change to a trip proposed by Pengoo (table {@code assistant_proposals}, V228). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantProposal {
    private UUID id;
    private UUID conversationId;
    private UUID turnId;
    private UUID messageId;
    private UUID tripId;
    private UUID requestedBy;
    /** {@link com.ds.goroute.type.AssistantProposalAction} name. */
    private String action;
    /** JSON {dayNumber, toDayNumber, startTime, endTime, placeId, placeName, activityId, activityName, category}. */
    private String payload;
    private String summary;
    /** {@link com.ds.goroute.type.AssistantProposalStatus} name. */
    private String status;
    private UUID resolvedBy;
    private LocalDateTime resolvedAt;
    private UUID resultActivityId;
    private String failureReason;
    private LocalDateTime createdAt;
    /** Joined from users; not a column. */
    private String resolvedByName;
}
