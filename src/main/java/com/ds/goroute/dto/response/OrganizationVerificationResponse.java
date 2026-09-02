package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class OrganizationVerificationResponse {
    private UUID organizationId;
    private String status;
    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;
    private String reason;
    private List<OrganizationVerificationDocumentResponse> documents;
    /** Human-readable note about what the upload endpoint accepts (images only). */
    private String uploadHint;
}
