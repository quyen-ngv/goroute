package com.ds.goroute.thirdparty.openai;

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
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "openai")
@RequiredArgsConstructor
@Slf4j
public class OpenAiClient implements AiClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${AI_API_KEY:${OPENAI_API_KEY:}}")
    private String apiKey;

    @Value("${AI_MODEL:gpt-5-mini}")
    private String model;

    @Value("${AI_API_URL:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${AI_MAX_TOKENS:2500}")
    private int maxCompletionTokens;

    @Override
    public Optional<String> completeJson(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OPENAI_API_KEY/AI_API_KEY is not configured; using local AI-trip fallback");
            return Optional.empty();
        }

        try {
            OpenAiChatRequest request = OpenAiChatRequest.builder()
                    .model(model)
                    .maxCompletionTokens(maxCompletionTokens)
                    .messages(List.of(
                            OpenAiChatMessage.builder()
                                    .role("system")
                                    .content("Return one valid JSON object only. " + systemPrompt)
                                    .build(),
                            OpenAiChatMessage.builder().role("user").content(userPrompt).build()))
                    .responseFormat(OpenAiResponseFormat.builder().type("json_object").build())
                    .build();

            OpenAiChatResponse response = restClientBuilder.build()
                    .post()
                    .uri(apiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(OpenAiChatResponse.class);

            if (response == null || response.getChoices() == null) {
                return Optional.empty();
            }
            return response.getChoices().stream()
                    .map(OpenAiChoice::getMessage)
                    .filter(message -> message != null && message.getContent() != null)
                    .map(OpenAiChatMessage::getContent)
                    .filter(content -> !content.isBlank())
                    .findFirst();
        } catch (Exception e) {
            log.warn("OpenAI request failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class OpenAiChatRequest {
        private String model;
        private List<OpenAiChatMessage> messages;
        @JsonProperty("max_completion_tokens")
        private Integer maxCompletionTokens;
        @JsonProperty("response_format")
        private OpenAiResponseFormat responseFormat;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class OpenAiChatMessage {
        private String role;
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class OpenAiResponseFormat {
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class OpenAiChatResponse {
        private List<OpenAiChoice> choices;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class OpenAiChoice {
        private OpenAiChatMessage message;
    }
}
