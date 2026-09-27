package com.ds.goroute.quest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** A participant in a run (§3.13). Verification and reward are per member, not per run. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestRunMember {

    private UUID id;
    private UUID runId;
    private UUID userId;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
    private String verification;
    private Integer presenceCheckpoints;
    private boolean rewarded;
}
