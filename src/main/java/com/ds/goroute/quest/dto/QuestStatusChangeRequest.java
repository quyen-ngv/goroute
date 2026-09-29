package com.ds.goroute.quest.dto;

import com.ds.goroute.type.QuestStatus;
import jakarta.validation.constraints.NotNull;

/**
 * An operational status change on a quest that has been live: pause, suspend, archive, or bring
 * it back to PUBLISHED. {@code expectedVersion} is the quest's {@code dataVersion} as last read.
 */
public record QuestStatusChangeRequest(
        @NotNull QuestStatus status,
        Long expectedVersion) {
}
