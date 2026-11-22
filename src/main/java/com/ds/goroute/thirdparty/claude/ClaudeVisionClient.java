package com.ds.goroute.thirdparty.claude;

import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import com.ds.goroute.config.ClaudeProperties;
import com.ds.goroute.thirdparty.ai.VisionAiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Receipt scan on Claude vision ({@code goroute.receipt-scan.provider=claude}). */
@Component
@ConditionalOnProperty(prefix = "goroute.receipt-scan", name = "provider", havingValue = "claude")
@RequiredArgsConstructor
@Slf4j
public class ClaudeVisionClient implements VisionAiClient {

    private final ClaudeProperties properties;
    private final ClaudeMessages messages;

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public Optional<String> completeJson(String systemPrompt, String userPrompt, byte[] image, String mimeType) {
        if (!isConfigured()) {
            log.warn("ANTHROPIC_API_KEY is not configured; receipt scan unavailable");
            return Optional.empty();
        }
        String system = "Return one valid JSON object only. " + systemPrompt;
        String data = Base64.getEncoder().encodeToString(image);
        ImageBlockParam picture = ImageBlockParam.builder()
                .source(Base64ImageSource.builder()
                        .mediaType(Base64ImageSource.MediaType.of(mimeType))
                        .data(data)
                        .build())
                .build();
        MessageCreateParams params = MessageCreateParams.builder()
                .model(properties.getVisionModel())
                .maxTokens(properties.getVisionMaxTokens())
                .system(system)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(properties.getVisionEffort())).build())
                .addUserMessageOfBlockParams(List.of(
                        ContentBlockParam.ofImage(picture),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(userPrompt).build())))
                .build();
        Map<String, Object> logRequest = new LinkedHashMap<>();
        logRequest.put("model", properties.getVisionModel());
        logRequest.put("max_tokens", properties.getVisionMaxTokens());
        logRequest.put("system", system);
        logRequest.put("output_config", Map.of("effort", properties.getVisionEffort()));
        logRequest.put("messages", List.of(Map.of("role", "user", "content", List.of(
                Map.of("type", "image", "source", Map.of("type", "base64", "media_type", mimeType,
                        "data", "<base64 " + data.length() + " chars>")),
                Map.of("type", "text", "text", userPrompt)))));
        return messages.complete(properties.getApiKey(), properties.getBaseUrl(), properties.getVisionTimeout(),
                "AI_VISION", "COMPLETE_JSON", params, logRequest);
    }
}
