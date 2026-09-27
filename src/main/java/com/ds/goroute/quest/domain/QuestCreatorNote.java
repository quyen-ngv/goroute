package com.ds.goroute.quest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A creator's private note on a checkpoint, jotted while surveying (§6.2). Never shown to players.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestCreatorNote {

    private UUID id;
    private UUID checkpointId;
    private String note;
    private UUID createdBy;
    private LocalDateTime createdAt;
}
