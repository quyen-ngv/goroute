package com.ds.goroute.quest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One option of a CHOICE / MULTI_CHOICE question. The answer is stored against this stable
 * {@code id}, never against an index, because the order is shuffled on every run (§3.12).
 * {@code correct} is server-only and never leaves through a public DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestQuestionChoice {

    private UUID id;
    private UUID questionId;
    private Integer sortOrder;
    private String content;
    private boolean correct;
}
