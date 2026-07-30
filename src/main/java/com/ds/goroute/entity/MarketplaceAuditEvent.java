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
public class MarketplaceAuditEvent {
    private UUID id;
    private UUID organizationId;
    private String entityType;
    private UUID entityId;
    private String action;
    private UUID actorUserId;
    private String actorType;
    private String reason;
    private String metadata;
    private String requestId;
    private LocalDateTime createdAt;
}
