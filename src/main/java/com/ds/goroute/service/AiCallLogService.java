package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.AiApiCallFilter;
import com.ds.goroute.dto.request.AiApiCallLogRequest;
import com.ds.goroute.dto.response.AiApiCallPageResponse;
import com.ds.goroute.dto.response.AiApiCallStatResponse;
import com.ds.goroute.dto.response.AiCreditStatus;
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
 * {@code {"input":..,"cachedInput":..,"cacheWrite5m":..,"cacheWrite1h":..,"output":..,"webSearchPer1k":..}}
 * in USD per 1M tokens (per 1,000 calls for web search). Cache reads and writes are subsets of the
 * input tokens; a missing cache-write price falls back to Anthropic's 1.25x / 2x of the input price.
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
    private final AiCreditService aiCreditService;

    public record Price(BigDecimal input, BigDecimal cachedInput, BigDecimal cacheWrite5m, BigDecimal cacheWrite1h,
                        BigDecimal output, BigDecimal webSearchPer1k) {
        private static final BigDecimal WRITE_5M_FACTOR = new BigDecimal("1.25");
        private static final BigDecimal WRITE_1H_FACTOR = BigDecimal.valueOf(2);

        BigDecimal cost(Usage usage) {
            long read = Math.min(usage.cached(), usage.input());
            long write5m = Math.min(usage.cacheWrite5m(), usage.input() - read);
            long write1h = Math.min(usage.cacheWrite1h(), usage.input() - read - write5m);
            long uncached = Math.max(0, usage.input() - read - write5m - write1h);
            BigDecimal cachedRate = cachedInput != null ? cachedInput : this.input;
            BigDecimal write5mRate = cacheWrite5m != null ? cacheWrite5m : rate(this.input).multiply(WRITE_5M_FACTOR);
            BigDecimal write1hRate = cacheWrite1h != null ? cacheWrite1h : rate(this.input).multiply(WRITE_1H_FACTOR);
            return rate(this.input).multiply(BigDecimal.valueOf(uncached))
                    .add(rate(cachedRate).multiply(BigDecimal.valueOf(read)))
                    .add(write5mRate.multiply(BigDecimal.valueOf(write5m)))
                    .add(write1hRate.multiply(BigDecimal.valueOf(write1h)))
                    .add(rate(this.output).multiply(BigDecimal.valueOf(usage.output())))
                    .divide(MILLION, 8, RoundingMode.HALF_UP)
                    .add(rate(webSearchPer1k).multiply(BigDecimal.valueOf(usage.searches())).divide(THOUSAND, 8, RoundingMode.HALF_UP))
                    .setScale(6, RoundingMode.HALF_UP);
        }

        private static BigDecimal rate(BigDecimal value) {
            return value == null ? BigDecimal.ZERO : value;
        }
    }

    /** Token counts to price; cached and the cache writes are parts of {@code input}. */
    public record Usage(long input, long cached, long cacheWrite5m, long cacheWrite1h, long output, long searches) {
    }

    /**
     * Reported by a worker. {@code userId} is used when the report does not name a user. Returns
     * the credit status after the charge, which the worker reads to stop a task out of credit.
     */
    public AiCreditStatus record(AiApiCallLogRequest r, UUID userId) {
        return record(AiApiCall.builder()
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
                .cacheWrite5mTokens(r.getCacheWrite5mTokens())
                .cacheWrite1hTokens(r.getCacheWrite1hTokens())
                .outputTokens(r.getOutputTokens())
                .webSearchCalls(r.getWebSearchCalls())
                .latencyMs(r.getLatencyMs())
                .startedAt(r.getStartedAt())
                .build());
    }

    /**
     * Writes the row, then charges it to the user's AI credits when its feature is billed.
     * Returns null when nothing was charged or the write failed.
     */
    public AiCreditStatus record(AiApiCall call) {
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
                    call.setCostUsd(price.cost(new Usage(nz(call.getInputTokens()), nz(call.getCachedInputTokens()),
                            nz(call.getCacheWrite5mTokens()), nz(call.getCacheWrite1hTokens()),
                            nz(call.getOutputTokens()), nz(call.getWebSearchCalls()))));
                }
            }
            call.setRequestPayload(cap(call.getRequestPayload()));
            call.setResponsePayload(cap(call.getResponsePayload()));
            call.setErrorPayload(cap(call.getErrorPayload()));
            mapper.upsert(call);
        } catch (Exception e) {
            log.warn("AI call log failed for {}/{} model={}: {}", call.getFeature(), call.getOperation(),
                    call.getModel(), e.getMessage());
            return null;
        }
        try {
            return aiCreditService.charge(call);
        } catch (Exception e) {
            log.warn("AI credit charge failed for {}/{} user={}: {}", call.getFeature(), call.getOperation(),
                    call.getUserId(), e.getMessage());
            return null;
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
                cost = cost.add(price.cost(new Usage(row.getUnpricedInputTokens(), row.getUnpricedCachedInputTokens(),
                        row.getUnpricedCacheWrite5mTokens(), row.getUnpricedCacheWrite1hTokens(),
                        row.getUnpricedOutputTokens(), row.getUnpricedWebSearchCalls())));
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
                        decimal(v, "cacheWrite5m"), decimal(v, "cacheWrite1h"),
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
