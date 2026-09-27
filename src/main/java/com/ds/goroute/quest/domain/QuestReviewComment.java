package com.ds.goroute.quest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** A reviewer's note (§6.2), quest-level or per-checkpoint, shown to the creator on denial. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestReviewComment {

    private UUID id;
    private UUID questId;
    private UUID questVersionId;
    private UUID checkpointId;
    private UUID reviewerUserId;
    private String comment;
    private LocalDateTime createdAt;
}
