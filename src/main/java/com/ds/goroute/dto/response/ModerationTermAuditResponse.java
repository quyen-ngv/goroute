package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One change to the term list. Present because this table decides whether a user may
 * publish, so "who changed what, and to what" has to be answerable.
 */
@Data
@Builder
public class ModerationTermAuditResponse {
    private UUID id;
    private UUID termId;
    private String action;
    private String beforeValue;
    private String afterValue;
    private UUID changedBy;
    private LocalDateTime changedAt;
}
