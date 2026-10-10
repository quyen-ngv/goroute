package com.ds.goroute.service;

import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.ImageCleanupMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class ImageStorageCleanupServiceTest {

    private final ImageCleanupMapper mapper = mock(ImageCleanupMapper.class);
    private final StorageService storage = mock(StorageService.class);
    private final ImageStorageCleanupService service =
            new ImageStorageCleanupService(mapper, new ObjectMapper(), storage);

    @Test
    void leavesAnUnreferencedObjectAloneUntilItIsOldEnoughToBeGarbage() {
        when(mapper.selectRows(anyString(), any())).thenReturn(List.of());
        when(storage.extractObjectKey(anyString())).thenAnswer(call -> call.getArgument(0));
        when(storage.listObjects("expenses/")).thenReturn(List.of(
                new StorageService.StoredObject("expenses/last-month.webp",
                        Instant.now().minus(Duration.ofDays(40))),
                // Uploaded a minute ago: the check-in that will name it may still be
                // somewhere between the upload and the POST, or in the offline queue.
                new StorageService.StoredObject("expenses/just-uploaded.webp",
                        Instant.now().minus(Duration.ofMinutes(1)))));

        var result = service.cleanupOrphanedImages(
                List.of("EXPENSE"), List.of("expenses/"), true, 500, false, null);

        assertThat(result.scannedObjectCount()).isEqualTo(2);
        assertThat(result.orphanKeys()).containsExactly("expenses/last-month.webp");
        assertThat(result.keptTooNewObjectCount()).isEqualTo(1);
        assertThat(result.minimumOrphanAgeDays()).isEqualTo(14);
        verify(storage, never()).deleteObjectKeys(any());
    }

    @Test
    void treatsAnUnknownWriteTimeAsBrandNew() {
        when(mapper.selectRows(anyString(), any())).thenReturn(List.of());
        when(storage.extractObjectKey(anyString())).thenAnswer(call -> call.getArgument(0));
        when(storage.listObjects("expenses/")).thenReturn(List.of(
                new StorageService.StoredObject("expenses/no-timestamp.webp", null)));

        var result = service.cleanupOrphanedImages(
                List.of("EXPENSE"), List.of("expenses/"), true, 500, false, null);

        assertThat(result.orphanKeys()).isEmpty();
        assertThat(result.keptTooNewObjectCount()).isEqualTo(1);
    }

    @Test
    void aCheckinEditLeavesItsDroppedPhotosToTheSweep() {
        UUID checkinId = UUID.randomUUID();
        when(storage.extractObjectKey(anyString()))
                .thenAnswer(call -> call.<String>getArgument(0).replace("https://cdn/", ""));
        when(mapper.selectRows(contains("entity_type = 'USER_CHECKIN'"), eq(checkinId)))
                .thenReturn(urlRows("https://cdn/kept.webp", "https://cdn/dropped.webp"));

        var result = service.deleteImagesForEntityRecord(
                "USER_CHECKIN_PHOTO", checkinId, List.of("https://cdn/kept.webp"));

        // A saved-category cover or a trip memory may point at the same file.
        assertThat(result.deletedKeys()).isEmpty();
        assertThat(result.referencedUrlCount()).isEqualTo(2);
        verify(storage, never()).deleteObjectKeys(any());
    }

    @Test
    void deletingAnAccountDeletesItsAvatarAtOnce() {
        UUID userId = UUID.randomUUID();
        when(storage.extractObjectKey(anyString()))
                .thenAnswer(call -> call.<String>getArgument(0).replace("https://cdn/", ""));
        when(mapper.selectRows(contains("FROM users WHERE id = ?"), eq(userId)))
                .thenReturn(urlRows("https://cdn/avatars/u/a.png"));

        var result = service.deleteImagesForEntityRecord("USER", userId);

        assertThat(result.deletedKeys()).containsExactly("avatars/u/a.png");
        verify(storage).deleteObjectKeys(List.of("avatars/u/a.png"));
    }

    @Test
    void theNightlySweepCoversEveryKnownPrefixAndSparesTheHardCodedLogo() {
        when(storage.extractObjectKey(anyString())).thenAnswer(call -> call.getArgument(0));
        when(mapper.selectRows(anyString(), any())).thenReturn(List.of());
        when(storage.listObjects(anyString())).thenReturn(List.of());
        Instant old = Instant.now().minus(Duration.ofDays(40));
        when(storage.listObjects("admin-media/")).thenReturn(List.of(
                new StorageService.StoredObject("admin-media/06a38eef-3fb4-4139-9c5e-3240ea368c66-app_icon.png", old),
                new StorageService.StoredObject("admin-media/unused.png", old)));
        when(storage.listObjects("places/")).thenReturn(List.of(
                new StorageService.StoredObject("places/p1/replaced.webp", old)));

        var deleted = service.sweepOrphans(500);

        assertThat(deleted).containsExactly("admin-media/unused.png", "places/p1/replaced.webp");
        verify(storage).listObjects("expenses/");
        verify(storage, never()).copyObjectKeys(any(), anyString());
    }

    @Test
    void aQuestPhotoFromTheSharedUploadDoorIsNotAnOrphan() {
        when(storage.extractObjectKey(anyString()))
                .thenAnswer(call -> call.<String>getArgument(0).replace("https://cdn/", ""));
        when(mapper.selectRows(anyString(), any())).thenReturn(List.of());
        // Checkpoint photos and an AR landmark, both uploaded under expenses/<userId>/.
        when(mapper.selectRows(contains("FROM quest_checkpoints"), any())).thenReturn(List.of(
                Map.of("ref", "[\"https://cdn/expenses/u/door.webp\"]"),
                Map.of("ref", "{\"markers\":[{\"imageUrl\":\"https://cdn/expenses/u/stele.png\"}]}")));
        Instant old = Instant.now().minus(Duration.ofDays(40));
        when(storage.listObjects("expenses/")).thenReturn(List.of(
                new StorageService.StoredObject("expenses/u/door.webp", old),
                new StorageService.StoredObject("expenses/u/stele.png", old),
                new StorageService.StoredObject("expenses/u/abandoned.webp", old)));

        var result = service.cleanupOrphanedImages(
                List.of("EXPENSE"), List.of("expenses/"), true, 500, false, null);

        assertThat(result.orphanKeys()).containsExactly("expenses/u/abandoned.webp");
    }

    @Test
    void aReHostedEventImageIsNotAnOrphan() {
        when(storage.extractObjectKey(anyString()))
                .thenAnswer(call -> call.<String>getArgument(0).replace("https://cdn/", ""));
        when(mapper.selectRows(anyString(), any())).thenReturn(List.of());
        when(mapper.selectRows(contains("FROM local_events"), any())).thenReturn(List.of(
                Map.of("ref", "https://cdn/events/e1/cover.webp"),
                Map.of("ref", "https://cdn/events/uploads/console.png")));
        Instant old = Instant.now().minus(Duration.ofDays(40));
        when(storage.listObjects("events/")).thenReturn(List.of(
                new StorageService.StoredObject("events/e1/cover.webp", old),
                new StorageService.StoredObject("events/uploads/console.png", old),
                new StorageService.StoredObject("events/e1/replaced.webp", old)));

        var result = service.cleanupOrphanedImages(
                List.of("LOCAL_EVENT"), List.of("events/"), true, 500, false, null);

        assertThat(result.orphanKeys()).containsExactly("events/e1/replaced.webp");
    }

    @Test
    void replacingAnArModelDeletesTheOldFileUnlessAnotherObjectUsesIt() {
        UUID assetId = UUID.randomUUID();
        when(storage.extractObjectKey(anyString()))
                .thenAnswer(call -> call.<String>getArgument(0).replace("https://cdn/", ""));
        when(mapper.selectRows(contains("WHERE id = ?"), eq(assetId))).thenReturn(List.of(Map.of(
                "glb_url", "https://cdn/quest-ar/old.glb",
                "usdz_url", "https://cdn/quest-ar/shared.usdz",
                "thumbnail_url", "https://cdn/quest-ar/thumb.png")));
        when(mapper.selectRows(contains("WHERE id <> ?"), eq(assetId)))
                .thenReturn(urlRows("https://cdn/quest-ar/shared.usdz"));

        var result = service.deleteImagesForEntityRecord("QUEST_AR_ASSET", assetId,
                List.of("https://cdn/quest-ar/new.glb", "https://cdn/quest-ar/thumb.png"));

        assertThat(result.deletedKeys()).containsExactly("quest-ar/old.glb");
        verify(storage).deleteObjectKeys(List.of("quest-ar/old.glb"));
    }

    @Test
    void theArSweepDeletesOnlyOldUnsavedUploadsWithoutABackup() {
        when(storage.extractObjectKey(anyString()))
                .thenAnswer(call -> call.<String>getArgument(0).replace("https://cdn/", ""));
        when(mapper.selectRows(anyString(), any())).thenReturn(List.of());
        when(mapper.selectRows(contains("FROM quest_ar_object_assets"), any()))
                .thenReturn(urlRows("https://cdn/quest-ar/saved.glb"));
        Instant old = Instant.now().minus(Duration.ofDays(5));
        when(storage.listObjects("quest-ar/")).thenReturn(List.of(
                new StorageService.StoredObject("quest-ar/saved.glb", old),
                new StorageService.StoredObject("quest-ar/abandoned.glb", old),
                new StorageService.StoredObject("quest-ar/uploading-now.glb", Instant.now()),
                new StorageService.StoredObject("quest-ar/unknown-time.glb", null)));

        var deleted = service.deleteOrphansUnder("quest-ar/", Duration.ofDays(2), 500);

        assertThat(deleted).containsExactly("quest-ar/abandoned.glb");
        verify(storage).deleteObjectKeys(List.of("quest-ar/abandoned.glb"));
        verify(storage, never()).copyObjectKeys(any(), anyString());
    }

    @Test
    void aFileNamedInAColumnNoSpecReadsIsNotAnOrphan() {
        when(storage.extractObjectKey(anyString()))
                .thenAnswer(call -> call.<String>getArgument(0).replace("https://cdn/", ""));
        when(mapper.selectRows(anyString(), any())).thenReturn(List.of());
        when(mapper.selectRows(contains("information_schema.columns"), any())).thenReturn(List.of(
                column("places", "menu"),
                column("marketplace_entity_versions", "snapshot")));
        // A menu photo as a JSON object, and a listing snapshot with the URL buried in nested JSON.
        when(mapper.selectRows(contains("FROM \"places\""), any())).thenReturn(List.of(
                Map.of("ref", "[{\"name\":\"Pho\",\"image\":\"https://cdn/places/p1/menu-1.webp?v=2\"}]")));
        // An older key holding the original file name, stored percent-encoded.
        when(mapper.selectRows(contains("FROM \"marketplace_entity_versions\""), any())).thenReturn(List.of(
                Map.of("ref", "{\"rooms\":[{\"images\":[\"https://cdn/places/p1/room.webp\","
                        + "\"https://cdn/places/p1/s%C3%A2n%20v%C6%B0%E1%BB%9Dn.jpg\"]}]}")));
        Instant old = Instant.now().minus(Duration.ofDays(40));
        when(storage.listObjects("places/")).thenReturn(List.of(
                new StorageService.StoredObject("places/p1/menu-1.webp", old),
                new StorageService.StoredObject("places/p1/room.webp", old),
                new StorageService.StoredObject("places/p1/sân vườn.jpg", old),
                new StorageService.StoredObject("places/p1/replaced.webp", old)));

        var result = service.cleanupOrphanedImages(
                List.of("PLACE"), List.of("places/"), true, 500, false, null);

        assertThat(result.orphanKeys()).containsExactly("places/p1/replaced.webp");
    }

    @Test
    void aSoftDeletedMediaRowStillReleasesItsFile() {
        when(storage.extractObjectKey(anyString()))
                .thenAnswer(call -> call.<String>getArgument(0).replace("https://cdn/", ""));
        when(mapper.selectRows(anyString(), any())).thenReturn(List.of());
        when(mapper.selectRows(contains("information_schema.columns"), any()))
                .thenReturn(List.of(column("media_assets", "url")));
        when(mapper.selectRows(contains("FROM \"media_assets\""), any()))
                .thenReturn(List.of(Map.of("ref", "https://cdn/admin-media/deleted.jpg")));
        Instant old = Instant.now().minus(Duration.ofDays(40));
        when(storage.listObjects("admin-media/")).thenReturn(List.of(
                new StorageService.StoredObject("admin-media/deleted.jpg", old)));

        var result = service.cleanupOrphanedImages(
                List.of("MEDIA_ASSET"), List.of("admin-media/"), true, 500, false, null);

        assertThat(result.orphanKeys()).containsExactly("admin-media/deleted.jpg");
        verify(mapper, never()).selectRows(contains("FROM \"media_assets\""), any());
    }

    @Test
    void aPrefixThatIsNotAPlainPathIsRefused() {
        when(mapper.selectRows(anyString(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.cleanupOrphanedImages(
                List.of("PLACE"), List.of("places/' OR 1=1 --"), true, 500, false, null))
                .isInstanceOf(BusinessException.class);
        verify(storage, never()).listObjects(anyString());
    }

    private static Map<String, Object> column(String table, String name) {
        return Map.of("table_name", table, "column_name", name);
    }

    private static List<Map<String, Object>> urlRows(String... urls) {
        return java.util.Arrays.stream(urls)
                .map(url -> Map.<String, Object>of("url", url))
                .toList();
    }
}
