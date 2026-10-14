package com.ds.goroute.service.assistant;

import com.ds.goroute.entity.AssistantHistoryMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantContextBuilderTest {

    /** Newest first, like the repository returns them. */
    private static List<AssistantHistoryMessage> newestFirst(long newest, int count) {
        List<AssistantHistoryMessage> rows = new ArrayList<>();
        for (long sequence = newest; sequence > newest - count; sequence--) {
            rows.add(AssistantHistoryMessage.builder().sequenceNo(sequence).build());
        }
        return rows;
    }

    private static long oldest(List<AssistantHistoryMessage> window) {
        return window.get(window.size() - 1).getSequenceNo();
    }

    @Test
    void shortHistoryIsSentWhole() {
        assertThat(AssistantContextBuilder.stableWindow(newestFirst(15, 15), 20)).hasSize(15);
    }

    @Test
    void windowOldestMessageMovesOnlyEveryTenMessages() {
        // 20 newest of 1..47 would start at 28; the window extends back to 20, the step boundary.
        List<AssistantHistoryMessage> window = AssistantContextBuilder.stableWindow(newestFirst(47, 30), 20);
        assertThat(window).hasSize(28);
        assertThat(oldest(window)).isEqualTo(20);

        // Nine more messages later the boundary is the same, so the prefix of the prompt is too.
        assertThat(oldest(AssistantContextBuilder.stableWindow(newestFirst(56, 30), 20))).isEqualTo(30);
        assertThat(oldest(AssistantContextBuilder.stableWindow(newestFirst(49, 30), 20))).isEqualTo(30);
        assertThat(oldest(AssistantContextBuilder.stableWindow(newestFirst(48, 30), 20))).isEqualTo(20);
    }

    @Test
    void windowNeverDropsBelowTheConfiguredMinimum() {
        for (long newest = 21; newest < 80; newest++) {
            List<AssistantHistoryMessage> window = AssistantContextBuilder.stableWindow(newestFirst(newest, 30), 20);
            assertThat(window.size()).isBetween(20, 20 + AssistantContextBuilder.HISTORY_WINDOW_STEP - 1);
            assertThat(window.get(0).getSequenceNo()).isEqualTo(newest);
        }
    }
}
