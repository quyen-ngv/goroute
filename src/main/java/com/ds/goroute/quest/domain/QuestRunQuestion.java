package com.ds.goroute.quest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** A member's attempt at one question (§6.2). Guesses and hints count per question, not per stop. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestRunQuestion {

    private UUID id;
    private UUID runId;
    private UUID memberId;
    private UUID questionId;
    private Integer guessCount;
    private Integer hintTierBought;
    private boolean revealed;
    private boolean skipped;
    private boolean correct;
    private LocalDateTime answeredAt;
}
