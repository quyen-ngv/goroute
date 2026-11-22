package com.ds.goroute.thirdparty.deepseek;

import com.ds.goroute.thirdparty.ai.AiClient;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import com.ds.goroute.thirdparty.ai.AiCallLoggingInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "deepseek", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DeepSeekClient implements AiClient {

    private final RestClient.Builder restClientBuilder;

    /** Optional so unit tests can build the client by hand; writes the ai_api_calls row. */
    @Autowired(required = false)
    private AiCallLoggingInterceptor aiCallLogging;

    private void addCallLogging(java.util.List<ClientHttpRequestInterceptor> interceptors) {
        if (aiCallLogging != null) {
            interceptors.add(aiCallLogging.forProvider("deepseek", "AI_TEXT", "COMPLETE_JSON"));
        }
    }

    @Value("${AI_CONNECT_TIMEOUT_SECONDS:10}")
    private int connectTimeoutSeconds;

    @Value("${AI_READ_TIMEOUT_SECONDS:90}")
    private int readTimeoutSeconds;

    /** The default RestClient has NO read timeout; a stalled AI call must fail, not hang a thread. */
    private org.springframework.http.client.ClientHttpRequestFactory aiRequestFactory() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutSeconds * 1000);
        factory.setReadTimeout(readTimeoutSeconds * 1000);
        return factory;
    }

    @Value("${DEEPSEEK_API_KEY:${AI_API_KEY:}}")
    private String apiKey;

    @Value("${DEEPSEEK_CHEAP_MODEL:${DEEPSEEK_MODEL:${AI_MODEL:deepseek-v4-flash}}}")
    private String model;

    @Value("${AI_API_URL:https://api.deepseek.com/chat/completions}")
    private String apiUrl;

    @Value("${AI_MAX_TOKENS:2500}")
    private int maxTokens;

    @Override
    public Optional<String> completeJson(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("DEEPSEEK_API_KEY is not configured; DeepSeek text calls return nothing");
            return Optional.empty();
        }

        try {
            DeepSeekChatRequest request = DeepSeekChatRequest.builder()
                    .model(model)
                    .maxTokens(maxTokens)
                    .messages(List.of(
                            DeepSeekChatMessage.builder().role("system").content(systemPrompt).build(),
                            DeepSeekChatMessage.builder().role("user").content(userPrompt).build()))
                    .responseFormat(DeepSeekResponseFormat.builder().type("json_object").build())
                    .thinking(DeepSeekThinking.builder().type("disabled").build())
                    .build();

            DeepSeekChatResponse response = restClientBuilder.clone().requestFactory(aiRequestFactory()).requestInterceptors(this::addCallLogging).build()
                    .post()
                    .uri(apiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(DeepSeekChatResponse.class);

            if (response == null || response.getChoices() == null) {
                return Optional.empty();
            }

            return response.getChoices().stream()
                    .map(DeepSeekChoice::getMessage)
                    .filter(message -> message != null && message.getContent() != null)
                    .map(DeepSeekChatMessage::getContent)
                    .filter(content -> !content.isBlank())
                    .findFirst();
        } catch (Exception e) {
            log.warn("DeepSeek request failed: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class DeepSeekChatRequest {
        private String model;
        private List<DeepSeekChatMessage> messages;
        @JsonProperty("max_tokens")
        private Integer maxTokens;
        @JsonProperty("response_format")
        private DeepSeekResponseFormat responseFormat;
        private DeepSeekThinking thinking;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class DeepSeekChatMessage {
        private String role;
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class DeepSeekResponseFormat {
        private String type;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class DeepSeekThinking {
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class DeepSeekChatResponse {
        private List<DeepSeekChoice> choices;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class DeepSeekChoice {
        private DeepSeekChatMessage message;
    }
}
