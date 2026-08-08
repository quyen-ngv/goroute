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
public class SocialLocationSubmissionEvent {
    private UUID id;
    private UUID userId;
    private UUID jobId;
    private String sourceUrl;
    private String eventType;
    private String reasonCode;
    private String details;
    private LocalDateTime createdAt;
}
