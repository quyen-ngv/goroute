package com.ds.goroute.thirdparty.ai;

import com.ds.goroute.entity.AiApiCall;
import com.ds.goroute.service.AiCallLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * RestClient interceptor that writes one {@code ai_api_calls} row per provider HTTP call, with
 * the raw request/response JSON and the provider's token usage. The response body is buffered
 * so the client still reads it normally.
 */
@Component
@RequiredArgsConstructor
public class AiCallLoggingInterceptor {
    /** Inline images (moderation sends the upload as a data URL) would dwarf the row. */
    private static final Pattern DATA_URL = Pattern.compile("data:[a-zA-Z0-9./+-]+;base64,[A-Za-z0-9+/=]{64,}");

    private final AiCallLogService logService;
    private final ObjectMapper objectMapper;

    /** {@code feature}/{@code operation} apply when the call is made outside an {@link AiCallScope}. */
    public ClientHttpRequestInterceptor forProvider(String provider, String feature, String operation) {
        return (request, body, execution) -> {
            long started = System.nanoTime();
            ClientHttpResponse response = null;
            byte[] responseBody = null;
            Exception failure = null;
            try {
                response = execution.execute(request, body);
                responseBody = response.getBody().readAllBytes();
                return new BufferedResponse(response, responseBody);
            } catch (IOException | RuntimeException e) {
                failure = e;
                throw e;
            } finally {
                long latencyMs = (System.nanoTime() - started) / 1_000_000;
                int status = response == null ? 0 : statusOf(response);
                record(provider, feature, operation, body, responseBody, status, failure, latencyMs);
            }
        };
    }

    /**
     * Writes the row for a call made without RestClient (the Anthropic SDK): the request and response
     * as the provider's JSON, the HTTP status (0 when none came back) and the failure, if any.
     */
    public void record(String provider, String feature, String operation, byte[] requestBody, byte[] responseBody,
                       int httpStatus, Exception failure, long latencyMs) {
        AiCallScope.Tag tag = AiCallScope.current();
        JsonNode request = parse(requestBody);
        JsonNode response = parse(responseBody);
        boolean ok = failure == null && httpStatus >= 200 && httpStatus < 300;
        JsonNode usage = response == null ? null : response.path("usage");
        Integer input = firstInt(usage, "prompt_tokens", "input_tokens");
        Integer cached = firstInt(usage, "prompt_cache_hit_tokens");
        if (cached == null && usage != null) {
            cached = firstInt(usage.path("prompt_tokens_details"), "cached_tokens");
        }
        Integer anthropicCacheRead = firstInt(usage, "cache_read_input_tokens");
        if (anthropicCacheRead != null) {
            // Anthropic counts cache reads outside input_tokens; the log's input includes them.
            cached = anthropicCacheRead;
            input = (input == null ? 0 : input) + anthropicCacheRead;
        }
        Integer output = firstInt(usage, "completion_tokens", "output_tokens");
        String error = ok ? null : json(Map.of(
                "httpStatus", httpStatus,
                "message", failure != null ? String.valueOf(failure.getMessage())
                        : responseBody == null ? "" : new String(responseBody, StandardCharsets.UTF_8)));
        logService.record(AiApiCall.builder()
                .userId(tag == null ? null : tag.userId())
                .traceId(tag == null ? null : tag.traceId())
                .feature(tag != null ? tag.feature() : feature)
                .operation(tag != null ? tag.operation() : operation)
                .provider(provider)
                .model(request == null ? null : text(request.path("model")))
                .status(ok ? "SUCCEEDED" : "FAILED")
                .requestPayload(redact(requestBody))
                .responsePayload(ok && response != null ? response.toString() : null)
                .errorPayload(error)
                .inputTokens(input)
                .cachedInputTokens(cached)
                .outputTokens(output)
                .latencyMs(latencyMs)
                .build());
    }

    private String redact(byte[] body) {
        if (body == null || body.length == 0) return null;
        String text = new String(body, StandardCharsets.UTF_8);
        text = DATA_URL.matcher(text).replaceAll(m -> "<data-url " + m.group().length() + " chars>");
        return parse(text.getBytes(StandardCharsets.UTF_8)) == null ? json(Map.of("raw", text)) : text;
    }

    private JsonNode parse(byte[] body) {
        if (body == null || body.length == 0) return null;
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer firstInt(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode()) return null;
        for (String field : fields) {
            JsonNode v = node.path(field);
            if (v.isNumber()) return v.asInt();
        }
        return null;
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static int statusOf(ClientHttpResponse response) {
        try {
            return response.getStatusCode().value();
        } catch (IOException e) {
            return 0;
        }
    }

    /** The interceptor consumed the stream; hand the client a replayable copy. */
    private record BufferedResponse(ClientHttpResponse delegate, byte[] body) implements ClientHttpResponse {
        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
