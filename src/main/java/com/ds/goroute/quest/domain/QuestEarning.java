package com.ds.goroute.quest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** A creator's income entry (§3.5, D20). Lives outside the Stars wallet so the year-end reset never touches it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestEarning {

    private UUID id;
    private UUID creatorId;
    private UUID questId;
    private UUID runId;
    private UUID fromUserId;
    private String source;
    private Integer amount;
    private String fundingSource;
    private String status;
    private LocalDateTime settleAt;
    private String sourceReference;
    private LocalDateTime createdAt;
}
