package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** A partner's saved quick reply for the marketplace inbox. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceMessageTemplate {
    private UUID id;
    private UUID organizationId;
    private String title;
    private String body;
    private Integer sortOrder;
    private UUID createdBy;
    private UUID updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
