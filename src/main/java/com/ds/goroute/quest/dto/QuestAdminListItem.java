package com.ds.goroute.quest.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A row in the console's quest management list: every creator's quests. {@code allowedStatuses}
 * and {@code deletable} are what an admin may do to it right now, so the console draws only the
 * actions the server will accept.
 */
public record QuestAdminListItem(
        UUID id,
        String origin,
        String status,
        String title,
        String coverUrl,
        UUID creatorId,
        int checkpointCount,
        String pausedBy,
        boolean pendingChangeReview,
        long dataVersion,
        LocalDateTime updatedAt,
        List<String> allowedStatuses,
        boolean deletable) {
}
