package com.ds.goroute.quest.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** A row in a creator's "my quests" list. No content beyond a title, the cover and where it is in the flow. */
public record QuestSummaryResponse(
        UUID id,
        String origin,
        String status,
        String title,
        String coverUrl,
        int checkpointCount,
        long dataVersion,
        LocalDateTime updatedAt) {
}
