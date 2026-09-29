package com.ds.goroute.quest.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A row in a creator's "my quests" list. No content beyond a title, the cover and where it is in
 * the flow, plus what the creator may do to it now: {@code allowedStatuses} (pause, archive,
 * resume) and whether it can be deleted.
 */
public record QuestSummaryResponse(
        UUID id,
        String origin,
        String status,
        String title,
        String coverUrl,
        int checkpointCount,
        long dataVersion,
        LocalDateTime updatedAt,
        String pausedBy,
        List<String> allowedStatuses,
        boolean deletable) {
}
