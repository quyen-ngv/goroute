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
public class OrganizationVerificationDocument {
    private UUID id;
    private UUID organizationId;
    private String kind;
    private String fileUrl;
    private String fileName;
    private String note;
    private String status;
    private String reviewNote;
    private UUID reviewedBy;
    private LocalDateTime reviewedAt;
    private UUID uploadedBy;
    private LocalDateTime createdAt;
}
