package com.ds.goroute.service;

import com.ds.goroute.dto.request.AiApiCallFilter;
import com.ds.goroute.dto.response.AiApiCallStatResponse;
import com.ds.goroute.entity.AiApiCall;
import com.ds.goroute.entity.AppConfig;
import com.ds.goroute.mapper.AiApiCallMapper;
import com.ds.goroute.repository.AppConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCallLogServiceTest {
    private final AiApiCallMapper mapper = mock(AiApiCallMapper.class);
    private final AppConfigRepository configs = mock(AppConfigRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiCreditService credits = mock(AiCreditService.class);
    private AiCallLogService service;

    @BeforeEach
    void setUp() {
        AppConfig price = new AppConfig();
        price.setKey("DeepSeek-Chat");
        price.setValue("{\"input\":0.28,\"cachedInput\":0.028,\"output\":0.42,\"webSearchPer1k\":10}");
        AppConfig claude = new AppConfig();
        claude.setKey("claude-sonnet-5-5");
        claude.setValue("{\"input\":2,\"cachedInput\":0.1,\"cacheWrite1h\":5,\"output\":10}");
        when(configs.findAdmin(eq(null), eq(AiCallLogService.PRICING_LABEL), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of(price, claude));
        service = new AiCallLogService(mapper, configs, objectMapper, credits);
    }

    @Test
    void pricesCachedInputApartAndAddsSearchCalls() {
        service.record(AiApiCall.builder().feature("AI_TRIP").operation("PLAN_ITINERARY").model("deepseek-chat")
                .status("SUCCEEDED").inputTokens(1_000_000).cachedInputTokens(400_000).outputTokens(100_000)
                .webSearchCalls(2).build());

        AiApiCall saved = captured();
        // 600k fresh * 0.28 + 400k cached * 0.028 + 100k out * 0.42 = 0.168 + 0.0112 + 0.042; + 2 searches * 0.01
        assertThat(saved.getCostUsd()).isEqualByComparingTo("0.2412");
        assertThat(saved.getTotalTokens()).isEqualTo(1_100_000);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void pricesCacheWritesWithTheirOwnRateOrTheAnthropicMultiplier() {
        service.record(AiApiCall.builder().feature("ASSISTANT").operation("ASSISTANT_TURN").model("claude-sonnet-5-5")
                .status("SUCCEEDED").inputTokens(1_000_000).cachedInputTokens(400_000)
                .cacheWrite5mTokens(200_000).cacheWrite1hTokens(100_000).outputTokens(10_000).build());

        // 300k fresh * 2 + 400k read * 0.1 + 200k 5m write * 2.5 (1.25x, no price) + 100k 1h write * 5
        // + 10k out * 10 = 0.6 + 0.04 + 0.5 + 0.5 + 0.1
        assertThat(captured().getCostUsd()).isEqualByComparingTo("1.74");
    }

    @Test
    void unknownModelIsStoredWithoutCost() {
        service.record(AiApiCall.builder().feature("AI_TRIP").operation("X").model("gpt-unknown")
                .status("SUCCEEDED").inputTokens(10).outputTokens(5).build());

        assertThat(captured().getCostUsd()).isNull();
    }

    @Test
    void oversizedPayloadStaysValidJson() throws Exception {
        String huge = "{\"prompt\":\"" + "x".repeat(AiCallLogService.MAX_PAYLOAD_CHARS + 10) + "\"}";
        service.record(AiApiCall.builder().feature("AI_TRIP").operation("X").status("SUCCEEDED")
                .requestPayload(huge).build());

        var node = objectMapper.readTree(captured().getRequestPayload());
        assertThat(node.path("truncated").asBoolean()).isTrue();
        assertThat(node.path("originalChars").asInt()).isEqualTo(huge.length());
    }

    @Test
    void aBrokenInsertNeverReachesTheCaller() {
        doThrow(new IllegalStateException("db down")).when(mapper).upsert(any());

        service.record(AiApiCall.builder().feature("AI_TRIP").operation("X").status("SUCCEEDED").build());
    }

    @Test
    void statsPriceRowsLoggedBeforeThePriceExisted() {
        AiApiCallStatResponse row = new AiApiCallStatResponse();
        row.setModel("deepseek-chat");
        row.setStoredCostUsd(new BigDecimal("1.000000"));
        row.setUnpricedInputTokens(1_000_000);
        row.setUnpricedOutputTokens(0);
        AiApiCallStatResponse unpriced = new AiApiCallStatResponse();
        unpriced.setModel("gpt-unknown");
        unpriced.setStoredCostUsd(BigDecimal.ZERO);
        unpriced.setUnpricedInputTokens(5);
        when(mapper.stats(any())).thenReturn(List.of(row, unpriced));

        List<AiApiCallStatResponse> stats = service.stats(new AiApiCallFilter());

        assertThat(stats.get(0).getCostUsd()).isEqualByComparingTo("1.28");
        assertThat(stats.get(0).isMissingPrice()).isFalse();
        assertThat(stats.get(1).isMissingPrice()).isTrue();
    }

    @Test
    void statsPriceUnpricedCacheWrites() {
        AiApiCallStatResponse row = new AiApiCallStatResponse();
        row.setModel("claude-sonnet-5-5");
        row.setStoredCostUsd(BigDecimal.ZERO);
        row.setUnpricedInputTokens(1_000_000);
        row.setUnpricedCacheWrite1hTokens(1_000_000);
        when(mapper.stats(any())).thenReturn(List.of(row));

        assertThat(service.stats(new AiApiCallFilter()).get(0).getCostUsd()).isEqualByComparingTo("5");
    }

    private AiApiCall captured() {
        ArgumentCaptor<AiApiCall> captor = ArgumentCaptor.forClass(AiApiCall.class);
        verify(mapper).upsert(captor.capture());
        return captor.getValue();
    }
}
