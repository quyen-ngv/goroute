package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class MarketplaceEntityVersionResponse {
    private UUID id;
    private String entityType;
    private UUID entityId;
    private Long versionNo;
    private String action;
    private Map<String, Object> snapshot;
    private List<String> changedFields;
    private UUID actorUserId;
    private String actorType;
    private String reason;
    private UUID restoredFromVersionId;
    private LocalDateTime createdAt;
}
