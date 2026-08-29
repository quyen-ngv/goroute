package com.ds.goroute.entity;

import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A takedown hides content everywhere it is public; it never deletes the row, because
 * appeals and escalations need the original.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentTakedown {
    private UUID id;
    private ModeratedContentType contentType;
    private UUID contentId;
    private UUID ownerId;
    private ModerationCategory category;
    private String reason;
    private UUID removedBy;
    private LocalDateTime removedAt;
    private UUID restoredBy;
    private LocalDateTime restoredAt;
}
