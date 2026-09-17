package com.ds.goroute.service;

import com.ds.goroute.mapper.ImageCleanupMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
    void anEditDeletesOnlyThePhotosItDroppedAndSparesThoseTheReviewStillShows() {
        UUID checkinId = UUID.randomUUID();
        when(storage.extractObjectKey(anyString()))
                .thenAnswer(call -> call.<String>getArgument(0).replace("https://cdn/", ""));
        when(mapper.selectRows(contains("entity_type = 'USER_CHECKIN'"), eq(checkinId)))
                .thenReturn(urlRows("https://cdn/kept.webp", "https://cdn/shared.webp", "https://cdn/dropped.webp"));
        when(mapper.selectRows(contains("m.entity_type = 'USER_REVIEW'"), eq(checkinId)))
                .thenReturn(urlRows("https://cdn/shared.webp"));

        var result = service.deleteImagesForEntityRecord(
                "USER_CHECKIN_PHOTO", checkinId, List.of("https://cdn/kept.webp"));

        assertThat(result.deletedKeys()).containsExactly("dropped.webp");
        verify(storage).deleteObjectKeys(List.of("dropped.webp"));
    }

    private static List<Map<String, Object>> urlRows(String... urls) {
        return java.util.Arrays.stream(urls)
                .map(url -> Map.<String, Object>of("url", url))
                .toList();
    }
}
