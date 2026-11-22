package com.ds.goroute.quest.dto;

import java.util.List;
import java.util.UUID;

/** Where the quest landed, its new lock value, and what the same caller may do next. */
public record QuestStatusChangeResponse(
        UUID questId,
        String status,
        long dataVersion,
        List<String> allowedStatuses,
        boolean deletable) {
}
