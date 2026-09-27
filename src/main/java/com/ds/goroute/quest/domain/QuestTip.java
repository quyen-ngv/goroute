package com.ds.goroute.quest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** A tip to a creator (§3.5). The money is booked to quest_earnings, never added to the wallet. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestTip {

    private UUID id;
    private UUID questId;
    private UUID runId;
    private UUID fromUserId;
    private UUID toCreatorId;
    private Integer amount;
    private String fundingSource;
    private String message;
    private boolean messagePublic;
    private LocalDateTime createdAt;
}
