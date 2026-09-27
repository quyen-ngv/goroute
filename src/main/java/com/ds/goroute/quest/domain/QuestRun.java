package com.ds.goroute.quest.domain;

import com.ds.goroute.type.QuestRunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One play-through (§6.2). Pins {@code questVersionId} so mid-play edits do not change it (D11). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestRun {

    private UUID id;
    private UUID questId;
    private UUID questVersionId;
    private UUID ownerUserId;
    private UUID tripId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime lastActivityAt;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;
    private Integer rankScore;
    private Long dataVersion;
    private LocalDateTime createdAt;

    public QuestRunStatus runStatus() {
        return QuestRunStatus.valueOf(status);
    }
}
