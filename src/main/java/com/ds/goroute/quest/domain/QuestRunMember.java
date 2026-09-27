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
    /**
     * Whether this member's app can show AR objects (§3.15), from the capability header it sent
     * when it joined. Without it an AR_OBJECT checkpoint plays as ARRIVE for this member.
     */
    private boolean arSupported;
}
