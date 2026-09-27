package com.ds.goroute.quest.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A reviewer's decision (§3.1). {@code checklist} is the §7.2 checklist they ticked (kept for
 * audit); {@code comments} are per-checkpoint notes the creator will see on denial.
 */
public record QuestReviewDecisionRequest(
        String decision,
        String reason,
        Map<String, Object> checklist,
        List<CheckpointComment> comments,
        Long expectedVersion) {

    public record CheckpointComment(UUID checkpointId, String comment) {
    }
}
