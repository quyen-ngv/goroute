package com.ds.goroute.thirdparty.claude;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClaudeClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${claude.api-key:}")
    private String apiKey;

    @Value("${claude.model:claude-3-5-haiku-20241022}")
    private String model;

    @Value("${claude.api-url:https://api.anthropic.com/v1/messages}")
    private String apiUrl;

    @Value("${claude.anthropic-version:2023-06-01}")
    private String anthropicVersion;

    @Value("${claude.max-tokens:2500}")
    private int maxTokens;

    public Optional<String> completeJson(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("CLAUDE_API_KEY is not configured; using local AI-trip fallback");
            return Optional.empty();
        }

        try {
            ClaudeMessageRequest request = ClaudeMessageRequest.builder()
                    .model(model)
                    .maxTokens(maxTokens)
                    .system(systemPrompt)
                    .messages(List.of(ClaudeChatMessage.builder()
                            .role("user")
                            .content(userPrompt)
                            .build()))
                    .build();

            ClaudeMessageResponse response = restClientBuilder.build()
                    .post()
                    .uri(apiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", anthropicVersion)
                    .body(request)
                    .retrieve()
                    .body(ClaudeMessageResponse.class);

            if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
                return Optional.empty();
            }

            return response.getContent().stream()
                    .filter(item -> "text".equals(item.getType()))
                    .map(ClaudeContentBlock::getText)
                    .filter(text -> text != null && !text.isBlank())
                    .findFirst();
        } catch (Exception e) {
            log.warn("Claude request failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class ClaudeMessageRequest {
        private String model;
        @JsonProperty("max_tokens")
        private Integer maxTokens;
        private String system;
        private List<ClaudeChatMessage> messages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ClaudeChatMessage {
        private String role;
        private String content;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ClaudeMessageResponse {
        private List<ClaudeContentBlock> content;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ClaudeContentBlock {
        private String type;
        private String text;
    }
}
