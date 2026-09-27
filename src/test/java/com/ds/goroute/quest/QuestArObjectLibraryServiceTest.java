package com.ds.goroute.quest;

import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.QuestArObjectAsset;
import com.ds.goroute.quest.dto.SaveQuestArObjectAssetRequest;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.service.QuestArObjectLibraryService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Removing AR library objects (§3.15) and the storage their files take. */
@DisplayName("AR object library storage")
class QuestArObjectLibraryServiceTest {

    private final QuestRepository repository = mock(QuestRepository.class);
    private final ImageStorageCleanupService cleanup = mock(ImageStorageCleanupService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QuestArObjectLibraryService library = new QuestArObjectLibraryService(repository,
            mock(StorageService.class), new MarketplaceJson(objectMapper), objectMapper,
            mock(RestTemplate.class), cleanup);

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(repository.findArObjectAsset(id)).thenReturn(Optional.of(QuestArObjectAsset.builder()
                .id(id).name("Scroll").glbUrl("https://cdn/quest-ar/a.glb").usdzUrl("https://cdn/quest-ar/a.usdz")
                .thumbnailUrl("https://cdn/quest-ar/a.png").clips("[]").tags("[]").active(true).dataVersion(3L)
                .build()));
        when(repository.updateArObjectAsset(any(), anyLong())).thenReturn(true);
        when(repository.deleteArObjectAsset(eq(id), anyLong())).thenReturn(true);
    }

    @Test
    @DisplayName("an object placed on a checkpoint cannot be deleted, and its files stay")
    void inUseRefused() {
        when(repository.countCheckpointsUsingArObjectAsset(id)).thenReturn(2);

        assertThatThrownBy(() -> library.delete(id, 3L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("2 checkpoints");
        verify(repository, never()).deleteArObjectAsset(any(), anyLong());
        verify(cleanup, never()).deleteImagesForEntityRecord(any(), any());
    }

    @Test
    @DisplayName("an unused object is deleted with its files")
    void unusedDeleted() {
        library.delete(id, 3L);

        verify(cleanup).deleteImagesForEntityRecord("QUEST_AR_ASSET", id);
        verify(repository).deleteArObjectAsset(id, 3L);
    }

    @Test
    @DisplayName("an edit hands the files it keeps to the cleanup, which removes the rest")
    void editReleasesDroppedFiles() {
        // Same GLB (no re-download), USDZ cleared, thumbnail kept.
        library.update(id, new SaveQuestArObjectAssetRequest("Scroll", null, "https://cdn/quest-ar/a.glb",
                null, "https://cdn/quest-ar/a.png", null, List.of(), true, 3L));

        verify(cleanup).deleteImagesForEntityRecord("QUEST_AR_ASSET", id,
                List.of("https://cdn/quest-ar/a.glb", "https://cdn/quest-ar/a.png"));
    }
}
