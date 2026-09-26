package com.ds.goroute.quest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One recorded review decision (§6.2). Written whenever a reviewer publishes, denies or field-tests. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestReviewDecisionRow {

    private UUID id;
    private UUID questId;
    private UUID questVersionId;
    private UUID reviewerUserId;
    private String decision;
    private String reason;
    /** JSON object of ticked checklist items. */
    private String checklist;
    private boolean selfApproved;
    private LocalDateTime createdAt;
}
