package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiApiCall {
    private UUID id;
    private UUID userId;
    private String feature;
    private String operation;
    private String correlationId;
    /** Groups the calls of one job: AI trip job id, social job id. */
    private String traceId;
    private String provider;
    private String model;
    private String status;
    private String requestPayload;
    private String responsePayload;
    private String errorPayload;
    private Integer candidateCount;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private Integer cachedInputTokens;
    private Integer webSearchCalls;
    /** USD; null when the model had no price in config AI_PRICING at insert time. */
    private BigDecimal costUsd;
    private Long latencyMs;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
