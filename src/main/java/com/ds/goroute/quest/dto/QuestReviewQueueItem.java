package com.ds.goroute.quest.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** A row in the review queue (§3.3). */
public record QuestReviewQueueItem(
        UUID questId,
        String origin,
        String status,
        String title,
        UUID creatorId,
        long dataVersion,
        LocalDateTime updatedAt) {
}
