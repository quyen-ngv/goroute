package com.ds.goroute.entity;

import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationAction;
import com.ds.goroute.type.ModerationCategory;
import com.ds.goroute.type.ModerationLayer;
import com.ds.goroute.type.ModerationVisibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One filter decision (MOD-08). False-positive rate, miss rate and the ranking of terms
 * that cause the most false blocks are all derived from this table, which is why it is
 * written from the day the filter is switched on rather than added later.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationDecision {
    private UUID id;
    private ModeratedContentType contentType;
    private UUID contentId;
    private String fieldLabel;
    private UUID userId;
    private ModerationVisibility visibility;
    private ModerationLayer layer;
    private ModerationAction decision;
    private ModerationCategory category;
    private UUID matchedTermId;
    private String matchedText;
    private String policyVersion;
    private LocalDateTime createdAt;
}
