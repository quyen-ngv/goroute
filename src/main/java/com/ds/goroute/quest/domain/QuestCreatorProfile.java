package com.ds.goroute.quest.domain;

import com.ds.goroute.type.QuestCreatorStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A creator's profile row (§6.2). A record, not a licence: it exists so terms acceptance,
 * quality score and the ACTIVE/SUSPENDED switch have somewhere to live.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestCreatorProfile {

    private UUID id;
    private UUID userId;
    private UUID organizationId;
    private String status;
    private LocalDateTime termsAcceptedAt;
    private String termsVersion;
    private Integer qualityScore;
    private LocalDateTime lastSubmittedAt;
    private Integer deniedStreak;
    private LocalDateTime blockedUntil;
    private Long dataVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public QuestCreatorStatus creatorStatus() {
        return QuestCreatorStatus.valueOf(status);
    }
}
