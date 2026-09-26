package com.ds.goroute.quest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Proof that a user unlocked a quest (§6.2 D19) — distinct from the Stars transaction that paid. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestEntitlement {

    private UUID id;
    private UUID questId;
    private UUID userId;
    private String fundingSource;
    private String sourceReference;
    private LocalDateTime createdAt;
}
