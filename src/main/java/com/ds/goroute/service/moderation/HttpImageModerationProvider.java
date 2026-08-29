package com.ds.goroute.service.moderation;

import com.ds.goroute.config.ImageModerationProperties;
import com.ds.goroute.type.ModerationCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Talks to an HTTP recognition service that answers with a confidence per group.
 *
 * <p>Selected explicitly with {@code goroute.moderation.image.provider=http}. This keeps it
 * mutually exclusive with provider-specific implementations such as OpenAI.
 */
@Component
@ConditionalOnProperty(prefix = "goroute.moderation.image", name = "provider", havingValue = "http")
@RequiredArgsConstructor
@Slf4j
public class HttpImageModerationProvider implements ImageModerationProvider {

    private final ImageModerationProperties properties;
    private final RestClient.Builder restClientBuilder;

    @Override
    public String name() {
        return "http";
    }

    @Override
    public Map<ModerationCategory, Double> score(byte[] bytes, String contentType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "image";
            }
        });

        Map<String, Object> response = restClientBuilder.build()
                .post()
                .uri(properties.getUrl())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .headers(headers -> {
                    if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey());
                    }
                })
                .body(body)
                .retrieve()
                .body(Map.class);

        return toScores(response);
    }

    private Map<ModerationCategory, Double> toScores(Map<String, Object> response) {
        Map<ModerationCategory, Double> scores = new EnumMap<>(ModerationCategory.class);
        if (response == null) {
            return scores;
        }
        Object raw = response.getOrDefault("scores", response);
        if (!(raw instanceof Map<?, ?> entries)) {
            return scores;
        }
        entries.forEach((key, value) -> {
            ModerationCategory category = readCategory(key);
            if (category != null && value instanceof Number number) {
                scores.merge(category, number.doubleValue(), Math::max);
            }
        });
        return scores;
    }

    private ModerationCategory readCategory(Object key) {
        try {
            return ModerationCategory.valueOf(String.valueOf(key).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            // An unknown group from the provider is data we cannot act on, not an error.
            return null;
        }
    }
}
