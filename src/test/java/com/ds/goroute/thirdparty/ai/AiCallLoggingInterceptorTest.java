package com.ds.goroute.thirdparty.ai;

import com.ds.goroute.entity.AiApiCall;
import com.ds.goroute.service.AiCallLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiCallLoggingInterceptorTest {
    private final AiCallLogService logService = mock(AiCallLogService.class);
    private final AiCallLoggingInterceptor interceptor = new AiCallLoggingInterceptor(logService, new ObjectMapper());

    @Test
    void anthropicCacheReadsAndWritesJoinTheInputAndKeepTheirSplit() {
        String response = """
                {"usage":{"input_tokens":50,"cache_read_input_tokens":900,"cache_creation_input_tokens":300,
                 "cache_creation":{"ephemeral_5m_input_tokens":100,"ephemeral_1h_input_tokens":200},
                 "output_tokens":30}}""";

        AiApiCall call = record(response);

        assertThat(call.getInputTokens()).isEqualTo(1_250);
        assertThat(call.getCachedInputTokens()).isEqualTo(900);
        assertThat(call.getCacheWrite5mTokens()).isEqualTo(100);
        assertThat(call.getCacheWrite1hTokens()).isEqualTo(200);
        assertThat(call.getOutputTokens()).isEqualTo(30);
    }

    @Test
    void anthropicCacheWriteWithoutSplitCountsAsFiveMinutes() {
        AiApiCall call = record("{\"usage\":{\"input_tokens\":10,\"cache_creation_input_tokens\":40,\"output_tokens\":1}}");

        assertThat(call.getInputTokens()).isEqualTo(50);
        assertThat(call.getCachedInputTokens()).isZero();
        assertThat(call.getCacheWrite5mTokens()).isEqualTo(40);
        assertThat(call.getCacheWrite1hTokens()).isZero();
    }

    @Test
    void openAiUsageHasNoCacheWrites() {
        AiApiCall call = record("""
                {"usage":{"prompt_tokens":100,"completion_tokens":5,"prompt_tokens_details":{"cached_tokens":60}}}""");

        assertThat(call.getInputTokens()).isEqualTo(100);
        assertThat(call.getCachedInputTokens()).isEqualTo(60);
        assertThat(call.getCacheWrite5mTokens()).isNull();
        assertThat(call.getCacheWrite1hTokens()).isNull();
    }

    private AiApiCall record(String response) {
        interceptor.record("anthropic", "ASSISTANT", "TURN", "{\"model\":\"m\"}".getBytes(StandardCharsets.UTF_8),
                response.getBytes(StandardCharsets.UTF_8), 200, null, 5);
        ArgumentCaptor<AiApiCall> captor = ArgumentCaptor.forClass(AiApiCall.class);
        verify(logService).record(captor.capture());
        return captor.getValue();
    }
}
