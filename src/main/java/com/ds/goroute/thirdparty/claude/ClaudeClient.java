package com.ds.goroute.thirdparty.claude;

import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.ds.goroute.config.ClaudeProperties;
import com.ds.goroute.thirdparty.ai.AiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Text completions on Claude ({@code ai.provider=claude}); today only AI text moderation calls it. */
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "claude")
@RequiredArgsConstructor
@Slf4j
public class ClaudeClient implements AiClient {

    private final ClaudeProperties properties;
    private final ClaudeMessages messages;

    @Override
    public Optional<String> completeJson(String systemPrompt, String userPrompt) {
        if (!properties.isConfigured()) {
            log.warn("ANTHROPIC_API_KEY is not configured; Claude text calls return nothing");
            return Optional.empty();
        }
        String system = "Return one valid JSON object only. " + systemPrompt;
        MessageCreateParams params = MessageCreateParams.builder()
                .model(properties.getTextModel())
                .maxTokens(properties.getTextMaxTokens())
                .system(system)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(properties.getTextEffort())).build())
                .addUserMessage(userPrompt)
                .build();
        Map<String, Object> logRequest = new LinkedHashMap<>();
        logRequest.put("model", properties.getTextModel());
        logRequest.put("max_tokens", properties.getTextMaxTokens());
        logRequest.put("system", system);
        logRequest.put("output_config", Map.of("effort", properties.getTextEffort()));
        logRequest.put("messages", List.of(Map.of("role", "user", "content", userPrompt)));
        return messages.complete(properties.getApiKey(), properties.getBaseUrl(), properties.getTextTimeout(),
                "AI_TEXT", "COMPLETE_JSON", params, logRequest);
    }
}
