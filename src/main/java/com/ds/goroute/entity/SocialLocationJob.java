package com.ds.goroute.entity;

import com.ds.goroute.type.SocialLocationJobStatus;
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
    private SocialLocationJobStatus status;
    private String pythonJobId;
    private String language;
    private String requestPayload;
    private String resultPayload;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}
