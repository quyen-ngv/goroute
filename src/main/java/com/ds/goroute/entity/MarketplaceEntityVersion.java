package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceEntityVersion {
    private UUID id;
    private String entityType;
    private UUID entityId;
    private Long versionNo;
    private String action;
    private String snapshot;
    private String changedFields;
    private UUID actorUserId;
    private String actorType;
    private String reason;
    private UUID restoredFromVersionId;
    private LocalDateTime createdAt;
}
