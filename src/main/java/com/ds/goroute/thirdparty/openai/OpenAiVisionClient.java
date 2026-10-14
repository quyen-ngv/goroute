package com.ds.goroute.thirdparty.openai;

import com.ds.goroute.thirdparty.ai.AiCallLoggingInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One image plus instructions in, one JSON object out. Always OpenAI, whatever {@code ai.provider}
 * says: the text provider (DeepSeek by default) cannot read images.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiVisionClient {

    private final RestClient.Builder restClientBuilder;

    /** Optional so unit tests can build the client by hand; writes the ai_api_calls row. */
    @Autowired(required = false)
    private AiCallLoggingInterceptor aiCallLogging;

    @Value("${RECEIPT_SCAN_API_KEY:${OPENAI_API_KEY:}}")
    private String apiKey;

    @Value("${RECEIPT_SCAN_MODEL:gpt-5-mini}")
    private String model;

    @Value("${RECEIPT_SCAN_API_URL:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    /** Blank for models without reasoning; the field is then left out of the request. */
    @Value("${RECEIPT_SCAN_REASONING_EFFORT:low}")
    private String reasoningEffort;

    @Value("${RECEIPT_SCAN_MAX_TOKENS:2000}")
    private int maxCompletionTokens;

    @Value("${RECEIPT_SCAN_READ_TIMEOUT_SECONDS:45}")
    private int readTimeoutSeconds;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Empty when the key is missing, the call fails, or the model returns nothing. */
    public Optional<String> completeJson(String systemPrompt, String userPrompt, byte[] image, String mimeType) {
        if (!isConfigured()) {
            log.warn("RECEIPT_SCAN_API_KEY/OPENAI_API_KEY is not configured; receipt scan unavailable");
            return Optional.empty();
        }
        String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(image);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_completion_tokens", maxCompletionTokens);
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            body.put("reasoning_effort", reasoningEffort);
        }
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", "Return one valid JSON object only. " + systemPrompt),
                Map.of("role", "user", "content", List.of(
                        Map.of("type", "text", "text", userPrompt),
                        Map.of("type", "image_url", "image_url", Map.of("url", dataUrl, "detail", "high"))))));
        try {
            JsonNode response = restClientBuilder.clone()
                    .requestFactory(requestFactory())
                    .requestInterceptors(this::addCallLogging)
                    .build()
                    .post()
                    .uri(apiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) return Optional.empty();
            String content = response.path("choices").path(0).path("message").path("content").asText("");
            return content.isBlank() ? Optional.empty() : Optional.of(content);
        } catch (Exception e) {
            log.warn("OpenAI vision request failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void addCallLogging(List<ClientHttpRequestInterceptor> interceptors) {
        if (aiCallLogging != null) {
            interceptors.add(aiCallLogging.forProvider("openai", "AI_VISION", "COMPLETE_JSON"));
        }
    }

    /** The default RestClient has no read timeout; the user is waiting on this one. */
    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(readTimeoutSeconds * 1000);
        return factory;
    }
}
