package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Read projection for the admin verification queue (one row per organization awaiting a decision). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationQueueEntry {
    private UUID organizationId;
    private String displayName;
    private String legalName;
    private String organizationType;
    private LocalDateTime submittedAt;
    private Integer documentCount;
    private String ownerName;
    private String ownerEmail;
}
