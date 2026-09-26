package com.ds.goroute.quest.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A run as the player sees it (§3.8). Run-scoped and deliberately narrow: only the current
 * checkpoint carries coordinates and questions, cleared checkpoints carry their unlocked story,
 * and checkpoints still ahead are a count — never their coordinates. No question here carries an
 * answer, a correct-choice flag, or unbought hint text (rules 5.2/5.3). The offline buffer takes
 * only {@link #current}, one checkpoint ahead.
 */
public record QuestRunResponse(
        UUID runId,
        UUID questId,
        String status,
        int clearedCheckpoints,
        int totalCheckpoints,
        boolean completed,
        List<ClearedCheckpointView> cleared,
        CurrentCheckpointView current,
        int arrivalClientIntervalSeconds) {

    public record ClearedCheckpointView(UUID checkpointId, int sortOrder, String name, String story) {
    }

    public record CurrentCheckpointView(
            UUID checkpointId,
            int sortOrder,
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer radiusM,
            boolean requiresCheckin,
            boolean checkinDone,
            boolean unlocked,
            int stableStreak,
            List<RunQuestionView> questions) {
    }

    public record RunQuestionView(
            UUID questionId,
            int sortOrder,
            boolean required,
            boolean bonus,
            String type,
            String prompt,
            UUID imageMediaId,
            int guessCount,
            boolean answeredCorrectly,
            boolean skipped,
            /** Hint text only for tiers the player has bought; null otherwise. */
            String boughtHint,
            /** Choices in shuffled order, with stable ids and NO correctness flag. */
            List<RunChoiceView> choices) {
    }

    public record RunChoiceView(UUID id, String content) {
    }
}
