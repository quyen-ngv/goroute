package com.ds.goroute.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection details for OpenAI's image moderation API.
 *
 * <p>The key stays in deployment secrets. Thresholds and the enable switch remain in
 * {@code BusinessConfigKey}, so operations can tune policy without a release.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "goroute.moderation.image.openai")
public class OpenAiImageModerationProperties {

    private String apiKey;

    private String url = "https://api.openai.com/v1/moderations";

    private String model = "omni-moderation-latest";

}
