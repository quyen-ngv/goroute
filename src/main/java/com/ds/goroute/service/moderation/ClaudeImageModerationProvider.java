package com.ds.goroute.service.moderation;

import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import com.ds.goroute.config.ClaudeProperties;
import com.ds.goroute.thirdparty.claude.ClaudeMessages;
import com.ds.goroute.type.ModerationCategory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Claude vision as the image moderation backend ({@code goroute.moderation.image.provider=claude}).
 *
 * <p>Unlike OpenAI's moderation model, which only scores sexual and violent content, Claude scores
 * every group that has an image threshold ({@code MODERATION.IMAGE_THRESHOLD_*}): sexual, violence,
 * drugs/weapons, hate symbols and spam. The scores are the model's judgement asked for in a prompt,
 * not a trained classifier's probabilities, so thresholds deserve a check on real uploads before
 * blocking on them. Every call is billed per image.
 */
@Component
@ConditionalOnProperty(prefix = "goroute.moderation.image", name = "provider", havingValue = "claude")
@RequiredArgsConstructor
public class ClaudeImageModerationProvider implements ImageModerationProvider {

    static final List<ModerationCategory> SCORED = List.of(ModerationCategory.SEXUAL, ModerationCategory.VIOLENCE,
            ModerationCategory.DRUGS_WEAPONS, ModerationCategory.HATE_SYMBOL, ModerationCategory.SPAM);

    private static final Set<String> SUPPORTED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    static final String SYSTEM_PROMPT = """
            You moderate photos uploaded to a travel social app. Score how clearly the image shows each \
            group below, from 0 (absent) to 1 (unmistakably present). Ordinary travel photos are not \
            violations: beaches and swimwear, food and drink, temples, statues and art, festivals, \
            street and night scenes, historical weapons in museums. Return one JSON object only:
            {"SEXUAL": number, "VIOLENCE": number, "DRUGS_WEAPONS": number, "HATE_SYMBOL": number, "SPAM": number}
            - SEXUAL: nudity, sexual acts or sexualised poses.
            - VIOLENCE: gore, injuries, graphic violence or cruelty to people or animals.
            - DRUGS_WEAPONS: illegal drugs or their use; weapons shown as a threat or offered for sale.
            - HATE_SYMBOL: extremist or hate symbols, gestures or slogans.
            - SPAM: the image is mainly an advertisement, promotion, price list, or phone/QR/contact overlay.""";

    private static final String USER_PROMPT = "Score this image.";

    private final ClaudeProperties properties;
    private final ClaudeMessages messages;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "claude";
    }

    @Override
    public Map<ModerationCategory, Double> score(byte[] bytes, String contentType) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is required for Claude image moderation");
        }
        if (contentType == null || !SUPPORTED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Claude cannot read images of type " + contentType);
        }
        String data = Base64.getEncoder().encodeToString(bytes);
        ImageBlockParam picture = ImageBlockParam.builder()
                .source(Base64ImageSource.builder()
                        .mediaType(Base64ImageSource.MediaType.of(contentType))
                        .data(data)
                        .build())
                .build();
        MessageCreateParams params = MessageCreateParams.builder()
                .model(properties.getModerationModel())
                .maxTokens(properties.getModerationMaxTokens())
                .system(SYSTEM_PROMPT)
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.of(properties.getModerationEffort())).build())
                .addUserMessageOfBlockParams(List.of(
                        ContentBlockParam.ofImage(picture),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(USER_PROMPT).build())))
                .build();
        Map<String, Object> logRequest = new LinkedHashMap<>();
        logRequest.put("model", properties.getModerationModel());
        logRequest.put("max_tokens", properties.getModerationMaxTokens());
        logRequest.put("system", SYSTEM_PROMPT);
        logRequest.put("output_config", Map.of("effort", properties.getModerationEffort()));
        logRequest.put("messages", List.of(Map.of("role", "user", "content", List.of(
                Map.of("type", "image", "source", Map.of("type", "base64", "media_type", contentType,
                        "data", "<base64 " + data.length() + " chars>")),
                Map.of("type", "text", "text", USER_PROMPT)))));
        String answer = messages.complete(properties.getApiKey(), properties.getBaseUrl(),
                        properties.getModerationTimeout(), "IMAGE_MODERATION", "SCORE_IMAGE", params, logRequest)
                .orElseThrow(() -> new IllegalStateException("Claude image moderation returned no answer"));
        return scoresFrom(answer);
    }

    /** The groups the answer scored, clamped to 0..1; a group it left out stays absent. */
    Map<ModerationCategory, Double> scoresFrom(String answer) {
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Claude image moderation answer has no JSON object");
        }
        JsonNode json;
        try {
            json = objectMapper.readTree(answer.substring(start, end + 1));
        } catch (Exception e) {
            throw new IllegalStateException("Claude image moderation answer is not JSON", e);
        }
        Map<ModerationCategory, Double> scores = new EnumMap<>(ModerationCategory.class);
        for (ModerationCategory category : SCORED) {
            JsonNode value = json.path(category.name());
            if (value.isNumber()) {
                scores.put(category, Math.max(0d, Math.min(1d, value.asDouble())));
            }
        }
        return scores;
    }
}
