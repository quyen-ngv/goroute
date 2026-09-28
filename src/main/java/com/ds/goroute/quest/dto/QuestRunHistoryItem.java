package com.ds.goroute.quest.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One run in the player's play history: every quest they started, finished or left, newest first.
 * Opening one reads it through {@code GET /v1/api/quest-runs/{runId}}, which serves a finished run
 * read-only.
 *
 * @param clientRunId the phone's id when the run was played on the phone and uploaded; the app
 *                    matches it against runs it still holds so none is listed twice.
 */
public record QuestRunHistoryItem(
        UUID runId,
        UUID questId,
        String title,
        String coverUrl,
        String language,
        String status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime lastActivityAt,
        int clearedCheckpoints,
        int totalCheckpoints,
        boolean rewarded,
        String clientRunId) {
}
