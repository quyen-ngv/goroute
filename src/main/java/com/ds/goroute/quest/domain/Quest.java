package com.ds.goroute.quest.domain;

import com.ds.goroute.type.QuestOrigin;
import com.ds.goroute.type.QuestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The stable identity of a quest (§6.2). Holds nothing a player sees — only the pointers to the
 * live version ({@code publishedVersionId}) and the version being edited ({@code draftVersionId}),
 * the review status, and the optimistic lock ({@code dataVersion}, distinct from content version).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quest {

    private UUID id;
    private UUID creatorId;
    private String origin;
    private String status;
    private UUID publishedVersionId;
    private UUID draftVersionId;
    private boolean pendingChangeReview;
    private UUID translationGroupId;
    private String pausedBy;
    private boolean selfApproved;
    private Long dataVersion;
    private boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public QuestStatus questStatus() {
        return QuestStatus.valueOf(status);
    }

    public QuestOrigin questOrigin() {
        return QuestOrigin.valueOf(origin);
    }
}
