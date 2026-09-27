package com.ds.goroute.quest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** A finding clue one member bought (§3.14.1). Unique per (member, checkpoint, tier). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestRunClue {

    private UUID id;
    private UUID runId;
    private UUID memberId;
    private UUID checkpointId;
    private Integer tier;
    private Integer starsSpent;
    private UUID starTransactionId;
    private LocalDateTime boughtAt;
}
