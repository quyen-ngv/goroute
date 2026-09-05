package com.ds.goroute.dto.response;

import com.ds.goroute.type.SocialLocationJobStatus;
import com.ds.goroute.type.SocialLocationOperation;
import com.fasterxml.jackson.databind.JsonNode;
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
public class SocialLocationJobResponse {
    private UUID id;
    private String sourceUrl;
    private String platform;
    private SocialLocationOperation operation;
    private SocialLocationJobStatus status;
    private String pythonJobId;
    private String language;
    private String userTier;
    private Integer videoDurationSeconds;
    private Integer maxDurationSeconds;
    private JsonNode result;
    private UUID aiTripJobId;
    private Integer savedSpotCount;
    private String errorCode;
    private String errorMessage;
    private Integer attemptCount;
    private LocalDateTime deadlineAt;
    private LocalDateTime lastHeartbeatAt;
    private String failureStage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}
