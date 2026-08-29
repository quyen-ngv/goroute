package com.ds.goroute.entity;

import com.ds.goroute.type.FlagSeverity;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationCategory;
import com.ds.goroute.type.ModerationFlagSource;
import com.ds.goroute.type.ModerationFlagStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single item in the shared human review queue (MOD-06). Content is addressed by
 * (type, id) rather than a foreign key, so the keyword filter, image moderation and
 * user reports all land in the same queue without a table per content kind.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationFlag {
    private UUID id;
    private ModeratedContentType contentType;
    private UUID contentId;
    private UUID contentOwnerId;
    private ModerationFlagSource source;
    private ModerationCategory category;
    private FlagSeverity severity;
    private Integer priority;
    private String reason;
    private String contextSnapshot;
    private Integer reportCount;
    private ModerationFlagStatus status;
    private String resolutionNote;
    private UUID reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
