package com.ds.goroute.service.moderation;

import com.ds.goroute.config.OpenAiImageModerationProperties;
import com.ds.goroute.type.ModerationCategory;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI's free image moderation model.
 *
 * <p>OpenAI currently returns image scores for sexual and violence categories. It does
 * not provide reliable image classifications for the policy's drugs/weapons, hate-symbol,
 * or spam groups, so this provider deliberately leaves those scores absent rather than
 * inventing a mapping.
 */
@Component
@ConditionalOnProperty(prefix = "goroute.moderation.image", name = "provider", havingValue = "openai")
@RequiredArgsConstructor
public class OpenAiImageModerationProvider implements ImageModerationProvider {

    private static final String SEXUAL = "sexual";
    private static final String VIOLENCE = "violence";
    private static final String GRAPHIC_VIOLENCE = "violence/graphic";

    private final OpenAiImageModerationProperties properties;
    private final RestClient.Builder restClientBuilder;

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public Map<ModerationCategory, Double> score(byte[] bytes, String contentType) {
        requireApiKey();

        OpenAiModerationResponse response = restClientBuilder.build()
                .post()
                .uri(properties.getUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .body(new OpenAiModerationRequest(
                        properties.getModel(),
                        List.of(new ImageInput("image_url", new ImageUrl(dataUrl(bytes, contentType))))))
                .retrieve()
                .body(OpenAiModerationResponse.class);

        return scoresFrom(response);
    }

    private Map<ModerationCategory, Double> scoresFrom(OpenAiModerationResponse response) {
        Map<ModerationCategory, Double> scores = new EnumMap<>(ModerationCategory.class);
        if (response == null || response.results() == null) {
            return scores;
        }
        for (OpenAiModerationResult result : response.results()) {
            if (result == null || result.categoryScores() == null) {
                continue;
            }
            putScore(scores, ModerationCategory.SEXUAL, result.categoryScores().get(SEXUAL));
            putScore(scores, ModerationCategory.VIOLENCE, result.categoryScores().get(VIOLENCE));
            putScore(scores, ModerationCategory.VIOLENCE, result.categoryScores().get(GRAPHIC_VIOLENCE));
        }
        return scores;
    }

    private void putScore(Map<ModerationCategory, Double> scores, ModerationCategory category, Double value) {
        if (value != null) {
            scores.merge(category, value, Math::max);
        }
    }

    private String dataUrl(byte[] bytes, String contentType) {
        String safeContentType = contentType == null || contentType.isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : contentType;
        return "data:" + safeContentType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private void requireApiKey() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required for OpenAI image moderation");
        }
    }

    private record OpenAiModerationRequest(String model, List<ImageInput> input) {
    }

    private record ImageInput(String type, @JsonProperty("image_url") ImageUrl imageUrl) {
    }

    private record ImageUrl(String url) {
    }

    private record OpenAiModerationResponse(List<OpenAiModerationResult> results) {
    }

    private record OpenAiModerationResult(@JsonProperty("category_scores") Map<String, Double> categoryScores) {
    }
}
