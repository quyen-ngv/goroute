package com.ds.goroute.thirdparty.ai;

import com.ds.goroute.config.AiClientFallbackConfig;
import com.ds.goroute.config.ClaudeProperties;
import com.ds.goroute.thirdparty.claude.ClaudeClient;
import com.ds.goroute.thirdparty.claude.ClaudeMessages;
import com.ds.goroute.thirdparty.claude.ClaudeVisionClient;
import com.ds.goroute.thirdparty.deepseek.DeepSeekClient;
import com.ds.goroute.thirdparty.openai.OpenAiClient;
import com.ds.goroute.thirdparty.openai.OpenAiVisionClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class AiClientSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(ClaudeProperties.class, ClaudeProperties::new)
            .withUserConfiguration(
                    ClaudeMessages.class,
                    ClaudeClient.class,
                    DeepSeekClient.class,
                    OpenAiClient.class,
                    AiClientFallbackConfig.class);

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
                    assertThat(context.getBean(ClaudeProperties.class).getTextModel()).isEqualTo("claude-haiku-5-5");
                    assertThat(context.getBean(ClaudeProperties.class).getBaseUrl())
                            .isEqualTo("https://api.anthropic.com");
                });
    }

    @Test
    void claudeWithoutKeyReturnsNothingInsteadOfCalling() {
        contextRunner.withPropertyValues("ai.provider=claude")
                .run(context -> assertThat(context.getBean(AiClient.class).completeJson("system", "user")).isEmpty());
    }

    @Test
    void receiptScanUsesOpenAiVisionByDefault() {
        contextRunner.withUserConfiguration(OpenAiVisionClient.class, ClaudeVisionClient.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(VisionAiClient.class);
                    assertThat(context.getBean(VisionAiClient.class)).isInstanceOf(OpenAiVisionClient.class);
                });
    }

    @Test
    void receiptScanKeepsOpenAiVisionWhenTheSwitchIsDeepSeek() {
        contextRunner.withUserConfiguration(OpenAiVisionClient.class, ClaudeVisionClient.class)
                .withPropertyValues("goroute.receipt-scan.provider=deepseek")
                .run(context -> assertThat(context.getBean(VisionAiClient.class)).isInstanceOf(OpenAiVisionClient.class));
    }

    @Test
    void receiptScanUsesClaudeVisionFromProperty() {
        contextRunner.withUserConfiguration(OpenAiVisionClient.class, ClaudeVisionClient.class)
                .withPropertyValues("goroute.receipt-scan.provider=CLAUDE")
                .run(context -> {
                    assertThat(context).hasSingleBean(VisionAiClient.class);
                    assertThat(context.getBean(VisionAiClient.class)).isInstanceOf(ClaudeVisionClient.class);
                    assertThat(context.getBean(VisionAiClient.class).isConfigured()).isFalse();
                });
    }

    @Test
    void selectsOpenAiFromProperty() {
        contextRunner.withPropertyValues("ai.provider=OPENAI")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiClient.class);
                    assertThat(context.getBean(AiClient.class)).isInstanceOf(OpenAiClient.class);
                    assertThat(ReflectionTestUtils.getField(context.getBean(AiClient.class), "model"))
                            .isEqualTo("gpt-5-mini");
                    assertThat(ReflectionTestUtils.getField(context.getBean(AiClient.class), "apiUrl"))
                            .isEqualTo("https://api.openai.com/v1/chat/completions");
                });
    }

    @Test
    void unknownProviderUsesNonFatalFallbackClient() {
        contextRunner.withPropertyValues("ai.provider=unsupported")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiClient.class);
                    assertThat(context.getBean(AiClient.class).completeJson("system", "user")).isEmpty();
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
