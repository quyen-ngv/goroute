package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class VerificationQueueItemResponse {
    private UUID organizationId;
    private String displayName;
    private String legalName;
    private String organizationType;
    private LocalDateTime submittedAt;
    private Integer documentCount;
    private String ownerName;
    private String ownerEmail;
}
