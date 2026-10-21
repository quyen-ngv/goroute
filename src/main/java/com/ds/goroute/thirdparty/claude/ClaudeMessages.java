package com.ds.goroute.thirdparty.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.ds.goroute.thirdparty.ai.AiCallLoggingInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Claude through the official Anthropic SDK, shared by the Claude text and vision clients: one SDK
 * client per key, host and timeout, one {@code ai_api_calls} row per call (with the
 * {@code AiCallScope} tag, which the receipt-scan quota counts), and the answer's text.
 *
 * <p>Current Claude models think before answering (adaptive thinking) and spend {@code max_tokens}
 * on it, reject non-default {@code temperature}, and may decline a request with
 * {@code stop_reason: "refusal"}; a refusal is logged as a failure and returns empty.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClaudeMessages {

    public static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    public static final String DEFAULT_MODEL = "claude-haiku-5-5";

    private final ObjectMapper objectMapper;

    /** Optional so unit tests can build the clients by hand; writes the ai_api_calls row. */
    @Autowired(required = false)
    private AiCallLoggingInterceptor aiCallLogging;

    private final Map<String, AnthropicClient> clients = new ConcurrentHashMap<>();

    /**
     * @param logRequest what the row stores as the request: the parameters sent, with any image
     *                   replaced by a size marker
     * @return the text of the answer; empty when the call failed, was refused or wrote no text
     */
    public Optional<String> complete(String apiKey, String baseUrl, Duration timeout, String feature,
                                     String operation, MessageCreateParams params, Map<String, Object> logRequest) {
        long started = System.nanoTime();
        Message message = null;
        Exception failure = null;
        int status = 0;
        try {
            message = client(apiKey, baseUrl, timeout).messages().create(params);
            status = 200;
            if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
                failure = new IllegalStateException("Claude declined the request: "
                        + message.stopDetails().map(Object::toString).orElse("refusal"));
                log.warn("{}: {}", operation, failure.getMessage());
                return Optional.empty();
            }
            String text = message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(TextBlock::text)
                    .collect(Collectors.joining());
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (AnthropicServiceException e) {
            failure = e;
            status = e.statusCode();
            log.warn("Claude {} failed with HTTP {}: {}", operation, status, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            failure = e;
            log.warn("Claude {} failed: {}", operation, e.getMessage());
            return Optional.empty();
        } finally {
            record(feature, operation, logRequest, message, status, failure, started);
        }
    }

    private void record(String feature, String operation, Map<String, Object> logRequest, Message message,
                        int status, Exception failure, long started) {
        if (aiCallLogging == null) return;
        try {
            byte[] request = objectMapper.writeValueAsBytes(logRequest);
            byte[] response = message == null ? null : objectMapper.writeValueAsBytes(logResponse(message));
            aiCallLogging.record("anthropic", feature, operation, request, response, status, failure,
                    (System.nanoTime() - started) / 1_000_000);
        } catch (Exception e) {
            log.warn("AI call log not written for Claude {}: {}", operation, e.getMessage());
        }
    }

    /** The answer in the Messages API's own field names, which the call log reads usage from. */
    private static Map<String, Object> logResponse(Message message) {
        Usage usage = message.usage();
        Map<String, Object> tokens = new LinkedHashMap<>();
        tokens.put("input_tokens", usage.inputTokens());
        tokens.put("output_tokens", usage.outputTokens());
        usage.cacheReadInputTokens().ifPresent(v -> tokens.put("cache_read_input_tokens", v));
        usage.cacheCreationInputTokens().ifPresent(v -> tokens.put("cache_creation_input_tokens", v));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", message.model().asString());
        body.put("stop_reason", message.stopReason().map(StopReason::asString).orElse(null));
        body.put("content", message.content().stream().flatMap(block -> block.text().stream())
                .map(text -> Map.of("type", "text", "text", text.text())).toList());
        body.put("usage", tokens);
        return body;
    }

    private AnthropicClient client(String apiKey, String baseUrl, Duration timeout) {
        String host = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl;
        return clients.computeIfAbsent(apiKey + '|' + host + '|' + timeout, key -> AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(host)
                .timeout(timeout)
                .maxRetries(1)
                .build());
    }

    @PreDestroy
    void close() {
        List.copyOf(clients.values()).forEach(AnthropicClient::close);
        clients.clear();
    }
}
