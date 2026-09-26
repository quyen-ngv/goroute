package com.ds.goroute.quest;

import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.persistence.QuestRunRepository;
import com.ds.goroute.quest.service.QuestAutoPauseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QuestAutoPauseService")
class QuestAutoPauseServiceTest {

    @Mock private QuestRepository questRepository;
    @Mock private QuestRunRepository runRepository;
    @Mock private com.ds.goroute.service.BusinessConfigService config;
    private QuestAutoPauseService service;

    private final UUID questId = UUID.randomUUID();
    private final LocalDateTime since = LocalDateTime.now().minusHours(72);

    @BeforeEach
    void setUp() {
        service = new QuestAutoPauseService(questRepository, runRepository, config);
    }

    private Quest published() {
        return Quest.builder().id(questId).status("PUBLISHED").dataVersion(2L).build();
    }

    @Test
    @DisplayName("too small a sample never pauses, however bad the rate")
    void smallSampleNeverPauses() {
        when(runRepository.countRunsByStatusSince(eq(questId), eq("COMPLETED"), any())).thenReturn(0);
        when(runRepository.countRunsByStatusSince(eq(questId), eq("ABANDONED"), any())).thenReturn(4);

        assertThat(service.evaluateOne(published(), since, 20, 60)).isFalse();
        verify(questRepository, never()).updateStatus(any(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("a high abandon rate over enough runs auto-pauses by the system")
    void highDropoffPauses() {
        when(runRepository.countRunsByStatusSince(eq(questId), eq("COMPLETED"), any())).thenReturn(5);
        when(runRepository.countRunsByStatusSince(eq(questId), eq("ABANDONED"), any())).thenReturn(20);
        when(questRepository.updateStatus(eq(questId), eq(2L), eq("PAUSED"), eq("SYSTEM"), any())).thenReturn(true);

        // 20/25 = 80% abandon, over the 20-run minimum and the 60% threshold.
        assertThat(service.evaluateOne(published(), since, 20, 60)).isTrue();
        verify(questRepository).updateStatus(eq(questId), eq(2L), eq("PAUSED"), eq("SYSTEM"), any());
    }

    @Test
    @DisplayName("a healthy quest is left alone")
    void healthyStays() {
        when(runRepository.countRunsByStatusSince(eq(questId), eq("COMPLETED"), any())).thenReturn(40);
        when(runRepository.countRunsByStatusSince(eq(questId), eq("ABANDONED"), any())).thenReturn(10);

        assertThat(service.evaluateOne(published(), since, 20, 60)).isFalse();
        verify(questRepository, never()).updateStatus(any(), anyLong(), any(), any(), any());
    }
}
