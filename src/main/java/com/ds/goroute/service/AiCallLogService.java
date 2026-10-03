package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.AiApiCallFilter;
import com.ds.goroute.dto.request.AiApiCallLogRequest;
import com.ds.goroute.dto.response.AiApiCallPageResponse;
import com.ds.goroute.dto.response.AiApiCallStatResponse;
import com.ds.goroute.entity.AiApiCall;
import com.ds.goroute.entity.AppConfig;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.AiApiCallMapper;
import com.ds.goroute.repository.AppConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Log of every AI provider call: prompt, response, tokens, latency and cost.
 *
 * Writing never fails the caller: a broken log row must not break an AI trip or a moderation.
 * Cost comes from config label {@value #PRICING_LABEL}; key = model id in lower case, value JSON
 * {@code {"input":..,"cachedInput":..,"output":..,"webSearchPer1k":..}} in USD per 1M tokens
 * (per 1,000 calls for web search).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCallLogService {
    public static final String PRICING_LABEL = "AI_PRICING";
    /** A plan prompt is ~20-60 KB; anything far beyond that is not worth keeping whole. */
    static final int MAX_PAYLOAD_CHARS = 200_000;
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1_000);

    private final AiApiCallMapper mapper;
    private final AppConfigRepository configRepository;
    private final ObjectMapper objectMapper;

    public record Price(BigDecimal input, BigDecimal cachedInput, BigDecimal output, BigDecimal webSearchPer1k) {
        BigDecimal cost(long input, long cached, long output, long searches) {
            long uncached = Math.max(0, input - cached);
            BigDecimal cachedRate = cachedInput != null ? cachedInput : this.input;
            return rate(this.input).multiply(BigDecimal.valueOf(uncached))
                    .add(rate(cachedRate).multiply(BigDecimal.valueOf(Math.min(cached, input))))
                    .add(rate(this.output).multiply(BigDecimal.valueOf(output)))
                    .divide(MILLION, 8, RoundingMode.HALF_UP)
                    .add(rate(webSearchPer1k).multiply(BigDecimal.valueOf(searches)).divide(THOUSAND, 8, RoundingMode.HALF_UP))
                    .setScale(6, RoundingMode.HALF_UP);
        }

        private static BigDecimal rate(BigDecimal value) {
            return value == null ? BigDecimal.ZERO : value;
        }
    }

    /** Reported by a worker. {@code userId} is used when the report does not name a user. */
    public void record(AiApiCallLogRequest r, UUID userId) {
        record(AiApiCall.builder()
                .userId(r.getUserId() != null ? r.getUserId() : userId)
                .feature(r.getFeature())
                .operation(r.getOperation())
                .correlationId(r.getCorrelationId())
                .traceId(r.getTraceId())
                .provider(r.getProvider())
                .model(r.getModel())
                .status(r.getStatus())
                .requestPayload(json(r.getRequest()))
                .responsePayload(json(r.getResponse()))
                .errorPayload(json(r.getError()))
                .inputTokens(r.getInputTokens())
                .cachedInputTokens(r.getCachedInputTokens())
                .outputTokens(r.getOutputTokens())
                .webSearchCalls(r.getWebSearchCalls())
                .latencyMs(r.getLatencyMs())
                .startedAt(r.getStartedAt())
                .build());
    }

    public void record(AiApiCall call) {
        try {
            LocalDateTime now = LocalDateTime.now();
            if (call.getId() == null) call.setId(UUID.randomUUID());
            if (call.getCreatedAt() == null) call.setCreatedAt(now);
            if (call.getCompletedAt() == null) call.setCompletedAt(now);
            if (call.getStartedAt() == null && call.getLatencyMs() != null) {
                call.setStartedAt(call.getCompletedAt().minus(Duration.ofMillis(call.getLatencyMs())));
            }
            if (call.getTotalTokens() == null && (call.getInputTokens() != null || call.getOutputTokens() != null)) {
                call.setTotalTokens(nz(call.getInputTokens()) + nz(call.getOutputTokens()));
            }
            if (call.getCostUsd() == null) {
                Price price = prices().get(key(call.getModel()));
                if (price != null) {
                    call.setCostUsd(price.cost(nz(call.getInputTokens()), nz(call.getCachedInputTokens()),
                            nz(call.getOutputTokens()), nz(call.getWebSearchCalls())));
                }
            }
            call.setRequestPayload(cap(call.getRequestPayload()));
            call.setResponsePayload(cap(call.getResponsePayload()));
            call.setErrorPayload(cap(call.getErrorPayload()));
            mapper.upsert(call);
        } catch (Exception e) {
            log.warn("AI call log failed for {}/{} model={}: {}", call.getFeature(), call.getOperation(),
                    call.getModel(), e.getMessage());
        }
    }

    public AiApiCallPageResponse list(AiApiCallFilter filter, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 200));
        int safePage = Math.max(0, page);
        return AiApiCallPageResponse.builder()
                .items(mapper.search(filter, safeSize, safePage * safeSize))
                .total(mapper.count(filter))
                .page(safePage)
                .size(safeSize)
                .features(mapper.distinctFeatures())
                .models(mapper.distinctModels())
                .build();
    }

    public AiApiCall get(UUID id) {
        AiApiCall call = mapper.findById(id);
        if (call == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "AI call not found", HttpStatus.NOT_FOUND);
        }
        return call;
    }

    public List<AiApiCallStatResponse> stats(AiApiCallFilter filter) {
        Map<String, Price> prices = prices();
        List<AiApiCallStatResponse> rows = mapper.stats(filter);
        for (AiApiCallStatResponse row : rows) {
            BigDecimal cost = row.getStoredCostUsd() == null ? BigDecimal.ZERO : row.getStoredCostUsd();
            boolean unpriced = row.getUnpricedInputTokens() + row.getUnpricedOutputTokens() + row.getUnpricedWebSearchCalls() > 0;
            Price price = prices.get(key(row.getModel()));
            if (unpriced && price != null) {
                cost = cost.add(price.cost(row.getUnpricedInputTokens(), row.getUnpricedCachedInputTokens(),
                        row.getUnpricedOutputTokens(), row.getUnpricedWebSearchCalls()));
            }
            row.setCostUsd(cost.setScale(6, RoundingMode.HALF_UP));
            row.setMissingPrice(unpriced && price == null);
        }
        return rows;
    }

    /** Active AI_PRICING rows, keyed by lower-case model id. Unreadable rows are skipped. */
    public Map<String, Price> prices() {
        Map<String, Price> out = new HashMap<>();
        for (AppConfig row : configRepository.findAdmin(null, PRICING_LABEL, true, 500, 0)) {
            try {
                JsonNode v = objectMapper.readTree(row.getValue());
                out.put(key(row.getKey()), new Price(decimal(v, "input"), decimal(v, "cachedInput"),
                        decimal(v, "output"), decimal(v, "webSearchPer1k")));
            } catch (Exception e) {
                log.warn("AI_PRICING row {} is not valid JSON: {}", row.getKey(), e.getMessage());
            }
        }
        return out;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isNumber() || v.isTextual() ? new BigDecimal(v.asText()) : null;
    }

    private static String key(String model) {
        return model == null ? "" : model.trim().toLowerCase(Locale.ROOT);
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private String json(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    /** Keeps the column valid JSON: an oversized payload becomes {"truncated":true,"head":"..."}. */
    private String cap(String payload) {
        if (payload == null || payload.length() <= MAX_PAYLOAD_CHARS) return payload;
        Map<String, Object> wrapped = Map.of("truncated", true, "originalChars", payload.length(),
                "head", payload.substring(0, MAX_PAYLOAD_CHARS));
        try {
            return objectMapper.writeValueAsString(wrapped);
        } catch (Exception e) {
            return null;
        }
    }
}
