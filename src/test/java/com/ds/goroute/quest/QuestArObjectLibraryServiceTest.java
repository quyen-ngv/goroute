package com.ds.goroute.quest;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.QuestArObjectAsset;
import com.ds.goroute.quest.dto.SaveCreatorArObjectRequest;
import com.ds.goroute.quest.dto.SaveQuestArObjectAssetRequest;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.service.QuestArObjectLibraryService;
import com.ds.goroute.quest.service.QuestCreatorGate;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.ds.goroute.type.BusinessConfigKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestTemplate;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
    private final StorageService storage = mock(StorageService.class);
    private final QuestCreatorGate creatorGate = mock(QuestCreatorGate.class);
    private final BusinessConfigService config = mock(BusinessConfigService.class);
    private final QuestArObjectLibraryService library = new QuestArObjectLibraryService(repository,
            storage, new MarketplaceJson(objectMapper), objectMapper, mock(RestTemplate.class), cleanup,
            creatorGate, config);
    private final UUID creator = UUID.randomUUID();
    private final String folder = "quest-ar/u/" + creator + "/";

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(repository.findArObjectAsset(id)).thenReturn(Optional.of(QuestArObjectAsset.builder()
                .id(id).name("Scroll").glbUrl("https://cdn/quest-ar/a.glb").usdzUrl("https://cdn/quest-ar/a.usdz")
                .thumbnailUrl("https://cdn/quest-ar/a.png").clips("[]").tags("[]").active(true).dataVersion(3L)
                .build()));
        when(repository.updateArObjectAsset(any(), anyLong())).thenReturn(true);
        when(repository.deleteArObjectAsset(eq(id), anyLong())).thenReturn(true);
        when(config.getInt(BusinessConfigKey.QUEST_AR_CREATOR_MAX_OBJECTS)).thenReturn(20);
        when(config.getInt(BusinessConfigKey.QUEST_AR_MAX_MODEL_MB)).thenReturn(50);
        when(storage.extractObjectKey(anyString()))
                .thenAnswer(call -> call.<String>getArgument(0).replace("https://cdn/", ""));
        when(storage.urlFor(anyString())).thenAnswer(call -> "https://cdn/" + call.getArgument(0));
    }

    /** A GLB with only a JSON chunk: a static triangle mesh, which passes the spec. */
    private static byte[] glb() {
        byte[] body = "{\"asset\":{\"version\":\"2.0\"},\"accessors\":[{\"count\":300}],\"meshes\":[{\"primitives\":[{\"indices\":0}]}]}"
                .getBytes(StandardCharsets.UTF_8);
        int padded = (body.length + 3) / 4 * 4;
        ByteBuffer buffer = ByteBuffer.allocate(20 + padded).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0x46546C67).putInt(2).putInt(20 + padded).putInt(padded).putInt(0x4E4F534A);
        buffer.put(body);
        while (buffer.hasRemaining()) {
            buffer.put((byte) ' ');
        }
        return buffer.array();
    }

    private static SaveCreatorArObjectRequest creatorObject(String glbUrl) {
        return new SaveCreatorArObjectRequest(" Lantern ", null, glbUrl, null, null, null);
    }

    @Test
    @DisplayName("a creator's object is read back from their own folder and kept private to them")
    void creatorCreates() {
        when(storage.readObject(folder + "a.glb")).thenReturn(glb());
        QuestArObjectAsset[] inserted = new QuestArObjectAsset[1];
        doAnswer(call -> inserted[0] = call.getArgument(0)).when(repository).insertArObjectAsset(any());
        when(repository.findArObjectAsset(any())).thenAnswer(call -> Optional.ofNullable(inserted[0]));

        library.createForCreator(creator, creatorObject("https://cdn/" + folder + "a.glb"));

        ArgumentCaptor<QuestArObjectAsset> saved = ArgumentCaptor.forClass(QuestArObjectAsset.class);
        verify(repository).insertArObjectAsset(saved.capture());
        assertThat(saved.getValue().getOwnerUserId()).isEqualTo(creator);
        assertThat(saved.getValue().getName()).isEqualTo("Lantern");
        assertThat(saved.getValue().getGlbUrl()).isEqualTo("https://cdn/" + folder + "a.glb");
        assertThat(saved.getValue().getTriangles()).isEqualTo(100);
    }

    @Test
    @DisplayName("a file outside the creator's folder is refused and never read")
    void foreignFileRefused() {
        for (String url : List.of("https://cdn/quest-ar/library.glb",
                "https://cdn/quest-ar/u/" + UUID.randomUUID() + "/theirs.glb",
                "https://evil.example/model.glb")) {
            assertThatThrownBy(() -> library.createForCreator(creator, creatorObject(url)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("you uploaded here");
        }
        verify(storage, never()).readObject(anyString());
    }

    @Test
    @DisplayName("the model size limit is the config's AR_MAX_MODEL_MB")
    void sizeLimitFromConfig() {
        when(config.getInt(BusinessConfigKey.QUEST_AR_MAX_MODEL_MB)).thenReturn(1);
        MockMultipartFile big = new MockMultipartFile("file", "big.glb", "model/gltf-binary",
                new byte[1024 * 1024 + 1]);

        assertThatThrownBy(() -> library.upload(creator, QuestArObjectLibraryService.FileKind.GLB, big))
                .isInstanceOf(BusinessException.class).hasMessageContaining("larger than 1 MB");
        verify(storage, never()).uploadFile(anyString(), any(), anyString(), anyLong());
    }

    @Test
    @DisplayName("a creator at the limit cannot upload more")
    void limit() {
        when(repository.countArObjectAssetsByOwner(creator)).thenReturn(20);

        assertThatThrownBy(() -> library.uploadForCreator(creator, QuestArObjectLibraryService.FileKind.GLB, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("up to 20");
        verify(storage, never()).uploadFile(anyString(), any(), anyString(), anyLong());
    }

    @Test
    @DisplayName("a creator without AR access cannot upload")
    void gateChecked() {
        doThrow(new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "AR objects are not open to you yet"))
                .when(creatorGate).requireArCreationOpen(creator);

        assertThatThrownBy(() -> library.createForCreator(creator, creatorObject("https://cdn/" + folder + "a.glb")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("not open");
        verify(repository, never()).insertArObjectAsset(any());
    }

    @Test
    @DisplayName("a creator deletes only their own objects")
    void creatorDeletesOwnOnly() {
        // The shared library object from setUp has no owner.
        assertThatThrownBy(() -> library.deleteForCreator(creator, id)).isInstanceOf(BusinessException.class);
        verify(repository, never()).deleteArObjectAsset(any(), anyLong());

        UUID mine = UUID.randomUUID();
        when(repository.findArObjectAsset(mine)).thenReturn(Optional.of(QuestArObjectAsset.builder()
                .id(mine).ownerUserId(creator).dataVersion(1L).build()));
        when(repository.deleteArObjectAsset(eq(mine), anyLong())).thenReturn(true);
        library.deleteForCreator(creator, mine);
        verify(cleanup).deleteImagesForEntityRecord("QUEST_AR_ASSET", mine);
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
