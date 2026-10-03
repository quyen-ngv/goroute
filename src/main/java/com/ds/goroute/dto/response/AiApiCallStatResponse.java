package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;

/** Usage of one (feature, operation, provider, model) over the filtered period. */
@Data
public class AiApiCallStatResponse {
    private String feature;
    private String operation;
    private String provider;
    private String model;
    private long calls;
    private long failedCalls;
    private long inputTokens;
    private long cachedInputTokens;
    private long outputTokens;
    private long webSearchCalls;
    private double avgLatencyMs;
    /** Stored cost plus today's price applied to rows logged before the model had one. */
    private BigDecimal costUsd;
    /** True when some rows still have no cost because the model has no AI_PRICING row. */
    private boolean missingPrice;

    @JsonIgnore private BigDecimal storedCostUsd;
    @JsonIgnore private long unpricedInputTokens;
    @JsonIgnore private long unpricedCachedInputTokens;
    @JsonIgnore private long unpricedOutputTokens;
    @JsonIgnore private long unpricedWebSearchCalls;
}
