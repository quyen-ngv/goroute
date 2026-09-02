package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** A partner rule that turns into a chat message when a booking reaches the trigger moment. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceScheduledMessage {
    private UUID id;
    private UUID organizationId;
    private UUID templateId;
    private String triggerType;
    private Integer offsetHours;
    private String body;
    private String appliesTo;
    private String status;
    private UUID createdBy;
    private UUID updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** Projected, not stored: last run of this rule and how many of its runs actually sent. */
    private LocalDateTime lastRunAt;
    private Long sentCount;
}
