package com.ds.goroute.service.moderation;

import com.ds.goroute.config.ClaudeProperties;
import com.ds.goroute.thirdparty.claude.ClaudeMessages;
import com.ds.goroute.type.ModerationCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaudeImageModerationProviderTest {

    private final ClaudeMessages messages = mock(ClaudeMessages.class);

    @Test
    @SuppressWarnings("unchecked")
    void scoresEveryThresholdedGroupAndLogsTheImageAsASizeMarker() {
        when(messages.complete(anyString(), anyString(), any(), eq("IMAGE_MODERATION"), eq("SCORE_IMAGE"), any(), any()))
                .thenReturn(Optional.of("""
                        Here you go: {"SEXUAL": 0.9, "VIOLENCE": 1.4, "DRUGS_WEAPONS": 0.1,
                        "HATE_SYMBOL": 0, "SPAM": -1, "HARASSMENT": 0.8}"""));

        Map<ModerationCategory, Double> scores = provider(properties("sk-ant")).score(new byte[]{1, 2, 3}, "image/jpeg");

        assertThat(scores).containsExactlyInAnyOrderEntriesOf(Map.of(
                ModerationCategory.SEXUAL, 0.9,
                ModerationCategory.VIOLENCE, 1.0,
                ModerationCategory.DRUGS_WEAPONS, 0.1,
                ModerationCategory.HATE_SYMBOL, 0.0,
                ModerationCategory.SPAM, 0.0));
        ArgumentCaptor<Map<String, Object>> logRequest = ArgumentCaptor.forClass(Map.class);
        verify(messages).complete(eq("sk-ant"), eq("https://api.anthropic.com"), any(), eq("IMAGE_MODERATION"),
                eq("SCORE_IMAGE"), any(), logRequest.capture());
        assertThat(logRequest.getValue().get("model")).isEqualTo("claude-haiku-5-5");
        assertThat(logRequest.getValue().toString()).contains("<base64 4 chars>").doesNotContain("AQID");
    }

    @Test
    void withoutKeyTheProviderIsUnavailable() {
        assertThatThrownBy(() -> provider(properties("")).score(new byte[]{1}, "image/png"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
        verify(messages, never()).complete(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void anUnreadableImageTypeIsRejectedBeforeAnyCall() {
        assertThatThrownBy(() -> provider(properties("sk-ant")).score(new byte[]{1}, "image/heic"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(messages, never()).complete(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void noAnswerMeansUnavailableNotClean() {
        when(messages.complete(any(), any(), any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider(properties("sk-ant")).score(new byte[]{1}, "image/webp"))
                .isInstanceOf(IllegalStateException.class);
    }

    private ClaudeImageModerationProvider provider(ClaudeProperties properties) {
        return new ClaudeImageModerationProvider(properties, messages, new ObjectMapper());
    }

    private static ClaudeProperties properties(String apiKey) {
        ClaudeProperties properties = new ClaudeProperties();
        properties.setApiKey(apiKey);
        return properties;
    }
}
