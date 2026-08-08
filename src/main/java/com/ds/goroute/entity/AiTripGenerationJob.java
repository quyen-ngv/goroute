package com.ds.goroute.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AiTripGenerationJob {
    private UUID id;
    private UUID userId;
    private String idempotencyKey;
    private String requestHash;
    private String requestPayload;
    private String locale;
    private String attemptId;
    private String pythonJobId;
    private String status;
    private String stage;
    private Integer progress;
    private String quotaStatus;
    private UUID createdTripId;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
