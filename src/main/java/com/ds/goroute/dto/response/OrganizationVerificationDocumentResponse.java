package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class OrganizationVerificationDocumentResponse {
    private UUID id;
    private String kind;
    private String fileUrl;
    private String fileName;
    private String note;
    private String status;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
