package com.ds.goroute.dto.response;

import com.ds.goroute.type.ModerationAction;
import com.ds.goroute.type.ModerationCategory;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ModerationTermResponse {
    private UUID id;
    private String term;
    private String normalizedTerm;
    private ModerationCategory category;
    private ModerationAction action;
    private String language;
    private Boolean isExemption;
    private String note;
    private Boolean isActive;
    private Long dataVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
