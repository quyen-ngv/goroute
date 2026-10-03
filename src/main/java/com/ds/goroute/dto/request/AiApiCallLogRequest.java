package com.ds.goroute.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** One AI provider call reported by a worker. Payloads are stored as sent (capped in size). */
@Data
public class AiApiCallLogRequest {
    @NotBlank @Size(max = 64) private String feature;
    @NotBlank @Size(max = 128) private String operation;
    /** Idempotency key of this call; a retried report with the same key overwrites the row. */
    @Size(max = 128) private String correlationId;
    @Size(max = 128) private String traceId;
    private UUID userId;
    @Size(max = 64) private String provider;
    @Size(max = 200) private String model;
    /** SUCCEEDED or FAILED. */
    @NotBlank @Size(max = 32) private String status;
    private JsonNode request;
    private JsonNode response;
    private JsonNode error;
    @PositiveOrZero private Integer inputTokens;
    @PositiveOrZero private Integer cachedInputTokens;
    @PositiveOrZero private Integer outputTokens;
    @PositiveOrZero private Integer webSearchCalls;
    @PositiveOrZero private Long latencyMs;
    private LocalDateTime startedAt;
}
