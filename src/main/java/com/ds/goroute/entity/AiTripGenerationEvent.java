package com.ds.goroute.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AiTripGenerationEvent {
    private Long id;
    private UUID jobId;
    private String attemptId;
    private String stage;
    private String status;
    private Integer progress;
    private String messageKey;
    private String params;
    private LocalDateTime createdAt;
}
