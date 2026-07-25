package com.ds.goroute.thirdparty.ai;

import com.ds.goroute.thirdparty.claude.ClaudeClient;
import com.ds.goroute.thirdparty.deepseek.DeepSeekClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class AiClientSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(ClaudeClient.class, DeepSeekClient.class);

    @Test
    void selectsDeepSeekByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AiClient.class);
            assertThat(context.getBean(AiClient.class)).isInstanceOf(DeepSeekClient.class);
            assertThat(ReflectionTestUtils.getField(context.getBean(AiClient.class), "model"))
                    .isEqualTo("deepseek-v4-flash");
            assertThat(ReflectionTestUtils.getField(context.getBean(AiClient.class), "apiUrl"))
                    .isEqualTo("https://api.deepseek.com/chat/completions");
        });
    }

    @Test
    void selectsClaudeFromProperty() {
        contextRunner.withPropertyValues("ai.provider=claude")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiClient.class);
                    assertThat(context.getBean(AiClient.class)).isInstanceOf(ClaudeClient.class);
                    assertThat(ReflectionTestUtils.getField(context.getBean(AiClient.class), "model"))
                            .isEqualTo("claude-3-5-haiku-20241022");
                    assertThat(ReflectionTestUtils.getField(context.getBean(AiClient.class), "apiUrl"))
                            .isEqualTo("https://api.anthropic.com/v1/messages");
                });
    }

    @Test
    void appliesSharedAiEnvironmentVariables() {
        contextRunner.withPropertyValues(
                        "AI_API_KEY=test-key",
                        "AI_MODEL=custom-model",
                        "AI_API_URL=https://example.com/chat",
                        "AI_MAX_TOKENS=1234")
                .run(context -> {
                    AiClient client = context.getBean(AiClient.class);
                    assertThat(ReflectionTestUtils.getField(client, "apiKey")).isEqualTo("test-key");
                    assertThat(ReflectionTestUtils.getField(client, "model")).isEqualTo("custom-model");
                    assertThat(ReflectionTestUtils.getField(client, "apiUrl")).isEqualTo("https://example.com/chat");
                    assertThat(ReflectionTestUtils.getField(client, "maxTokens")).isEqualTo(1234);
                });
    }
}
