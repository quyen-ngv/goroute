package com.ds.goroute.entity;

import com.ds.goroute.type.SocialLocationJobStatus;
import com.ds.goroute.type.SocialLocationOperation;
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
public class SocialLocationJob {
    private UUID id;
    private UUID userId;
    private String sourceUrl;
    private String sourceKey;
    private String platform;
    private SocialLocationOperation operation;
    private SocialLocationJobStatus status;
    private String pythonJobId;
    private String language;
    private String userTier;
    private Integer videoDurationSeconds;
    private Integer maxDurationSeconds;
    private String requestPayload;
    private String resultPayload;
    private UUID aiTripJobId;
    private Integer savedSpotCount;
    private String errorCode;
    private String errorMessage;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime deadlineAt;
    private LocalDateTime lastHeartbeatAt;
    private LocalDateTime lastReconciledAt;
    private String failureStage;
    private String errorDetails;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}
