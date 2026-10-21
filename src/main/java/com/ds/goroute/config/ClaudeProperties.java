package com.ds.goroute.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Connection details for Claude, used by the text client when {@code ai.provider=claude}, by the
 * receipt scan when {@code goroute.receipt-scan.provider=claude} and by image moderation when
 * {@code goroute.moderation.image.provider=claude}. An empty key switches Claude off: those calls
 * then return nothing (image moderation reports itself unavailable), as they do for the other
 * providers.
 *
 * <p>Claude thinks before answering and spends {@code max_tokens} on it, so the budgets leave room
 * for that; {@code effort} (low, medium, high, xhigh, max) is how much it thinks.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "goroute.claude")
public class ClaudeProperties {

    private String apiKey;

    @NotBlank
    private String baseUrl = "https://api.anthropic.com";

    @NotBlank
    private String textModel = "claude-haiku-5-5";

    @Min(1024)
    private long textMaxTokens = 4096;

    @NotBlank
    private String textEffort = "low";

    @NotNull
    private Duration textTimeout = Duration.ofSeconds(90);

    @NotBlank
    private String visionModel = "claude-haiku-5-5";

    @Min(1024)
    private long visionMaxTokens = 4096;

    @NotBlank
    private String visionEffort = "low";

    @NotNull
    private Duration visionTimeout = Duration.ofSeconds(45);

    @NotBlank
    private String moderationModel = "claude-haiku-5-5";

    @Min(1024)
    private long moderationMaxTokens = 4096;

    @NotBlank
    private String moderationEffort = "low";

    @NotNull
    private Duration moderationTimeout = Duration.ofSeconds(30);

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
