package com.ds.goroute.dto.response;

import com.ds.goroute.type.FlagSeverity;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationCategory;
import com.ds.goroute.type.ModerationFlagSource;
import com.ds.goroute.type.ModerationFlagStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One queue item. Carries the surrounding context because without it a reviewer cannot
 * decide: the same sentence can be a joke between friends or an insult, depending on
 * where it sits.
 */
@Data
@Builder
public class ModerationFlagResponse {
    private UUID id;
    private ModeratedContentType contentType;
    private UUID contentId;
    private UUID contentOwnerId;
    private String contentOwnerName;
    private ModerationFlagSource source;
    private ModerationCategory category;
    private FlagSeverity severity;
    private Integer priority;
    private String reason;
    private Object context;
    private Integer reportCount;
    private ModerationFlagStatus status;
    private String resolutionNote;
    private UUID reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private Boolean takenDown;
}
