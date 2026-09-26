package com.ds.goroute.quest;

import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.persistence.QuestRunRepository;
import com.ds.goroute.quest.service.QuestFieldTestService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
@DisplayName("QuestFieldTestService")
class QuestFieldTestServiceTest {

    @Mock private QuestRepository questRepository;
    @Mock private QuestRunRepository runRepository;
    @Mock private BusinessConfigService config;
    private QuestFieldTestService service;

    private final UUID questId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new QuestFieldTestService(questRepository, runRepository, config);
    }

    private Quest fieldTestQuest() {
        return Quest.builder().id(questId).draftVersionId(versionId).dataVersion(4L).status("FIELD_TEST").build();
    }

    @Test
    @DisplayName("below the completion threshold, the quest stays in field test")
    void belowThresholdStays() {
        when(runRepository.countCompletedRunsForVersion(questId, versionId)).thenReturn(3);

        assertThat(service.evaluateOne(fieldTestQuest(), 5)).isFalse();
        verify(questRepository, never()).updatePublish(any(), anyLong(), any(), any(Boolean.class), any());
    }

    @Test
    @DisplayName("at the completion threshold, the quest graduates to PUBLISHED")
    void atThresholdPublishes() {
        when(runRepository.countCompletedRunsForVersion(questId, versionId)).thenReturn(5);
        when(questRepository.updatePublish(eq(questId), eq(4L), eq(versionId), eq(false), any())).thenReturn(true);

        assertThat(service.evaluateOne(fieldTestQuest(), 5)).isTrue();
        verify(questRepository).updatePublish(eq(questId), eq(4L), eq(versionId), eq(false), any());
    }
}
